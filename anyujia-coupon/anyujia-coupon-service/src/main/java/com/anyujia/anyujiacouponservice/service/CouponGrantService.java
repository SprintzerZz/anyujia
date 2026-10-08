package com.anyujia.anyujiacouponservice.service;

import com.anyujia.anyujiacouponapi.domain.vo.CouponGrantTaskVO;
import org.springframework.web.multipart.MultipartFile;

public interface CouponGrantService {

    Long createTask(Long templateId, Integer userType, MultipartFile file);

    CouponGrantTaskVO getTask(Long taskId);

    void scanStalledTasks();
}
