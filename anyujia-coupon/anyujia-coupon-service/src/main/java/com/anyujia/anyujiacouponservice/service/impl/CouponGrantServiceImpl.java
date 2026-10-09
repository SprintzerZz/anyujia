package com.anyujia.anyujiacouponservice.service.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import com.anyujia.anyujiacouponapi.constants.CouponConstants;
import com.anyujia.anyujiacouponapi.domain.vo.CouponGrantTaskVO;
import com.anyujia.anyujiacouponservice.domain.entity.CouponGrantTask;
import com.anyujia.anyujiacouponservice.domain.entity.CouponTemplate;
import com.anyujia.anyujiacouponservice.mapper.CouponGrantTaskMapper;
import com.anyujia.anyujiacouponservice.mapper.CouponTemplateMapper;
import com.anyujia.anyujiacouponservice.mq.event.CouponGrantBatchEvent;
import com.anyujia.anyujiacouponservice.mq.producer.CouponGrantBatchProducer;
import com.anyujia.anyujiacouponservice.service.CouponGrantService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CouponGrantServiceImpl implements CouponGrantService {

    private static final String TASK_USER_SET_KEY = "coupon:grant:task:%d:users";
    private static final int BATCH_SIZE = 300;

    private final CouponTemplateMapper couponTemplateMapper;
    private final CouponGrantTaskMapper couponGrantTaskMapper;
    private final CouponGrantBatchProducer couponGrantBatchProducer;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTask(Long templateId, Integer userType, MultipartFile file) {
        CouponTemplate template = couponTemplateMapper.selectById(templateId);
        if (template == null || template.getStatus() != CouponConstants.TEMPLATE_ENABLED) {
            throw invalid("优惠券模板不存在或不可发放");
        }
        if (template.getApplicableUserType() != CouponConstants.APPLICABLE_USER_ALL
                && !template.getApplicableUserType().equals(userType)) {
            throw invalid("模板不适用当前用户身份");
        }
        Set<Long> userIds = parseUserIds(file);
        if (userIds.isEmpty()) {
            throw invalid("名单不能为空");
        }

        CouponGrantTask task = new CouponGrantTask();
        task.setTemplateId(templateId);
        task.setUserType(userType);
        task.setTotalCount(userIds.size());
        task.setSuccessCount(0);
        task.setFailureCount(0);
        task.setStatus(CouponConstants.GRANT_TASK_PROCESSING);
        task.setRemark("名单已解析，等待MQ消费");
        LocalDateTime now = LocalDateTime.now();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        couponGrantTaskMapper.insert(task);

        String redisKey = TASK_USER_SET_KEY.formatted(task.getId());
        redisTemplate.opsForSet().add(redisKey, userIds.stream().map(String::valueOf).toArray(String[]::new));
        dispatchAfterCommit(task.getId(), templateId, userType, new ArrayList<>(userIds));
        return task.getId();
    }

    @Override
    public CouponGrantTaskVO getTask(Long taskId) {
        CouponGrantTask task = couponGrantTaskMapper.selectById(taskId);
        if (task == null) {
            throw invalid("批量发券任务不存在");
        }
        CouponGrantTaskVO vo = new CouponGrantTaskVO();
        BeanUtils.copyProperties(task, vo);
        return vo;
    }

    @Override
    public void scanStalledTasks() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);
        List<CouponGrantTask> tasks = couponGrantTaskMapper.selectList(new LambdaQueryWrapper<CouponGrantTask>()
                .eq(CouponGrantTask::getStatus, CouponConstants.GRANT_TASK_PROCESSING)
                .lt(CouponGrantTask::getUpdatedAt, threshold)
                .last("limit 20"));
        for (CouponGrantTask task : tasks) {
            Long remaining = redisTemplate.opsForSet().size(TASK_USER_SET_KEY.formatted(task.getId()));
            if (remaining == null || remaining == 0) {
                couponGrantTaskMapper.updateStatus(task.getId(), CouponConstants.GRANT_TASK_FAILED,
                        "任务长时间未推进，请检查MQ消费状态");
            }
        }
    }

    private void dispatchAfterCommit(Long taskId, Long templateId, Integer userType, List<Long> userIds) {
        Runnable task = () -> dispatch(taskId, templateId, userType, userIds);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    private void dispatch(Long taskId, Long templateId, Integer userType, List<Long> userIds) {
        for (int start = 0; start < userIds.size(); start += BATCH_SIZE) {
            List<Long> batch = userIds.subList(start, Math.min(start + BATCH_SIZE, userIds.size()));
            String batchId = taskId + "-" + UUID.randomUUID();
            couponGrantBatchProducer.send(new CouponGrantBatchEvent(
                    batchId, taskId, templateId, userType, List.copyOf(batch), System.currentTimeMillis()));
        }
    }

    private Set<Long> parseUserIds(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename != null && (filename.endsWith(".xlsx") || filename.endsWith(".xls"))) {
            return parseExcelUserIds(file);
        }
        return parseTextUserIds(file);
    }

    private Set<Long> parseExcelUserIds(MultipartFile file) {
        Set<Long> result = new LinkedHashSet<>();
        try {
            EasyExcel.read(file.getInputStream(), new AnalysisEventListener<Map<Integer, String>>() {
                @Override
                public void invoke(Map<Integer, String> row, AnalysisContext context) {
                    row.values().stream()
                            .filter(value -> value != null && !value.isBlank() && !"userId".equalsIgnoreCase(value.trim()))
                            .map(value -> Long.valueOf(value.trim()))
                            .forEach(result::add);
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                }
            }).sheet().doRead();
            return result;
        } catch (RuntimeException | IOException ex) {
            throw invalid("Excel名单解析失败，请确认第一列或任意单元格为用户ID");
        }
    }

    private Set<Long> parseTextUserIds(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            Set<Long> result = new LinkedHashSet<>();
            String line;
            while ((line = reader.readLine()) != null) {
                for (String part : line.split("[,;\\s]+")) {
                    if (part == null || part.isBlank() || "userId".equalsIgnoreCase(part.trim())) {
                        continue;
                    }
                    result.add(Long.valueOf(part.trim()));
                }
            }
            return result;
        } catch (IOException | NumberFormatException ex) {
            throw invalid("名单解析失败，仅支持CSV或一行一个用户ID的文本文件");
        }
    }

    private ServiceException invalid(String message) {
        return new ServiceException(message, ResultCode.INVALID_PARA.getCode());
    }
}
