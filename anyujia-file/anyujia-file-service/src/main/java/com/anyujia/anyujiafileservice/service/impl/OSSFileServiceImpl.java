package com.anyujia.anyujiafileservice.service.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.common.utils.BinaryUtil;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import com.anyujia.anyujiafileservice.config.OSSProperties;
import com.anyujia.anyujiafileservice.constants.OSSConstants;
import com.anyujia.anyujiafileservice.domain.dto.FileDTO;
import com.anyujia.anyujiafileservice.domain.dto.SignDTO;
import com.anyujia.anyujiafileservice.service.IOSSFileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 基于阿里云 OSS 的文件服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = "storage.type", havingValue = "oss")
public class OSSFileServiceImpl implements IOSSFileService {

    /**
     * 日期目录格式，避免所有上传文件堆在同一个 OSS 目录下。
     */
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /**
     * 阿里云 OSS 客户端，由 OSS 自动配置类创建并注入。
     */
    private final OSSClient ossClient;

    /**
     * OSS 配置属性，从本地配置或 Nacos 配置中心读取。
     */
    private final OSSProperties ossProperties;

    /**
     * 上传文件到 OSS，并返回访问地址和对象元数据。
     *
     * @param file 上传文件
     * @return 文件上传结果
     */
    @Override
    public FileDTO upload(MultipartFile file) {
        // 判断上传文件对象是否为空，避免后续读取文件流时报空指针。
        if (file == null || file.isEmpty()) {
            // 文件为空属于客户端参数错误，直接抛出统一业务异常。
            throw new ServiceException(ResultCode.FILE_EMPTY);
        }

        // 获取客户端上传时携带的原始文件名。
        String originalFilename = file.getOriginalFilename();
        // 根据原始文件名生成 OSS 中实际保存的文件名。
        String fileName = buildFileName(originalFilename);
        // 根据路径前缀、日期目录和文件名生成 OSS 对象名称。
        String objectName = buildObjectName(fileName);

        // 使用 try-with-resources 自动关闭 MultipartFile 打开的输入流。
        try (InputStream inputStream = file.getInputStream()) {
            // 创建 OSS 对象元数据，用于设置长度、类型等上传信息。
            ObjectMetadata objectMetadata = new ObjectMetadata();
            // 不设置对象级 ACL，部分 Bucket 禁止 PutObject 时修改 object acl。
            // 设置文件大小，便于 OSS 正确记录对象内容长度。
            objectMetadata.setContentLength(file.getSize());
            // 设置文件 Content-Type，便于浏览器按正确类型预览或下载。
            objectMetadata.setContentType(file.getContentType());

            // 构造 OSS 上传请求，包含 bucket、objectName、文件流和元数据。
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    ossProperties.getBucketName(), objectName, inputStream, objectMetadata);
            // 执行 OSS 上传请求。
            PutObjectResult putObjectResult = ossClient.putObject(putObjectRequest);
            // 校验 OSS 返回结果，requestId 为空通常说明上传响应不完整。
            if (putObjectResult == null || !StringUtils.hasText(putObjectResult.getRequestId())) {
                // 记录异常上下文，便于排查 bucket、objectName 等请求信息。
                log.error("上传 OSS 异常，putObjectResult 未正常返回: {}", putObjectRequest);
                // 抛出统一的 OSS 上传失败业务异常。
                throw new ServiceException(ResultCode.OSS_UPLOAD_FAILED);
            }
        } catch (IOException | ClientException | OSSException e) {
            // 捕获文件流读取异常和 OSS 客户端异常，并记录对象名称。
            log.error("上传 OSS 异常，objectName: {}", objectName, e);
            // 屏蔽底层异常细节，对外返回统一的上传失败响应码。
            throw new ServiceException(ResultCode.OSS_UPLOAD_FAILED);
        }

        // 创建文件上传结果 DTO。
        FileDTO fileDTO = new FileDTO();
        // 设置文件最终访问地址。
        fileDTO.setUrl(ossProperties.getBaseUrl() + objectName);
        // 设置 OSS 文件 key，业务侧可用该值做后续删除或引用。
        fileDTO.setKey(objectName);
        // 设置文件名，只取 objectName 的最后一段。
        fileDTO.setName(new File(objectName).getName());
        // 设置 OSS 存储桶名称。
        fileDTO.setBucketName(ossProperties.getBucketName());
        // 设置 OSS 对象名称。
        fileDTO.setObjectName(objectName);
        // 设置服务端生成后的文件名。
        fileDTO.setFileName(fileName);
        // 设置客户端上传时的原始文件名。
        fileDTO.setOriginalFilename(originalFilename);
        // 设置文件内容类型。
        fileDTO.setContentType(file.getContentType());
        // 设置文件大小，单位字节。
        fileDTO.setSize(file.getSize());
        // 返回上传结果。
        return fileDTO;
    }

    /**
     * 获取 OSS 表单直传签名。
     *
     * @return OSS 表单直传签名信息
     */
    @Override
    public SignDTO getSign() {
        // 捕获签名生成过程中的所有异常，统一转换成业务异常。
        try {
            // 从 OSS 配置中获取 AccessKey ID。
            String accessKeyId = ossProperties.getAccessKeyId();
            // 从 OSS 配置中获取 AccessKey Secret。
            String accessKeySecret = ossProperties.getAccessKeySecret();
            // 获取当前 UTC 时间，后续 policy、credential、request date 都基于该时间生成。
            Instant now = Instant.now();
            // 创建接口返回对象。
            SignDTO signDTO = new SignDTO();
            // 设置 OSS 表单直传 host。
            signDTO.setHost(ossProperties.getBaseUrl());
            // 设置上传路径前缀，前端上传时可用该前缀拼接 object key。
            signDTO.setPathPrefix(normalizePathPrefix(ossProperties.getPathPrefix()));

            // 创建 Jackson ObjectMapper，用于把 policy Map 转成 JSON 字符串。
            ObjectMapper mapper = new ObjectMapper();
            // 创建 policy 根对象。
            Map<String, Object> policy = new HashMap<>();
            // 创建 policy 过期时间格式化器。
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(OSSConstants.SIGN_EXPIRE_TIME_FORMAT)
                    .withZone(java.time.ZoneOffset.UTC);
            // 计算 policy 过期时间。
            String expiration = formatter.format(now.plusSeconds(OSSConstants.SIGN_EXPERT));
            // 写入 policy 的 expiration 字段。
            policy.put("expiration", expiration);

            // 创建 policy 条件列表。
            List<Object> conditions = new ArrayList<>();
            // 创建 bucket 条件。
            Map<String, String> bucketCondition = new HashMap<>();
            // 限定表单上传的目标 bucket。
            bucketCondition.put("bucket", ossProperties.getBucketName());
            // 添加 bucket 条件。
            conditions.add(bucketCondition);

            // 创建签名版本条件。
            Map<String, String> signatureVersionCondition = new HashMap<>();
            // 指定使用 OSS V4 HMAC-SHA256 签名。
            signatureVersionCondition.put("x-oss-signature-version", "OSS4-HMAC-SHA256");
            // 添加签名版本条件。
            conditions.add(signatureVersionCondition);

            // 创建 credential 条件。
            Map<String, String> credentialCondition = new HashMap<>();
            // 创建 credential 日期格式化器。
            formatter = DateTimeFormatter.ofPattern(OSSConstants.SIGN_DATE_FORMAT)
                    .withZone(java.time.ZoneOffset.UTC);
            // 格式化 credential 日期，格式为 yyyyMMdd。
            String dateStr = formatter.format(now);
            // 拼接 OSS V4 credential。
            String xOSSCredential = accessKeyId + "/" + dateStr + "/" + ossProperties.getRegion()
                    + "/oss/aliyun_v4_request";
            // 设置响应中的 credential。
            signDTO.setXOSSCredential(xOSSCredential);
            // 写入 policy 的 credential 条件。
            credentialCondition.put("x-oss-credential", xOSSCredential);
            // 添加 credential 条件。
            conditions.add(credentialCondition);

            // 创建请求时间条件。
            Map<String, String> dateCondition = new HashMap<>();
            // 创建 OSS 请求时间格式化器。
            formatter = DateTimeFormatter.ofPattern(OSSConstants.SIGN_REQUEST_TIME_FORMAT)
                    .withZone(java.time.ZoneOffset.UTC);
            // 格式化 OSS 请求时间。
            String xOSSDate = formatter.format(now);
            // 设置响应中的请求时间。
            signDTO.setXOSSDate(xOSSDate);
            // 写入 policy 的请求时间条件。
            dateCondition.put("x-oss-date", xOSSDate);
            // 添加请求时间条件。
            conditions.add(dateCondition);
            // 限制上传文件大小范围，最大 1GB。
            conditions.add(Arrays.asList("content-length-range", 0, 1073741824));
            // 限定 OSS 上传成功后返回 HTTP 200。
            conditions.add(Arrays.asList("eq", "$success_action_status", "200"));
            // 写入 policy 的 conditions 字段。
            policy.put("conditions", conditions);
            // 将 policy 对象序列化为 JSON 字符串。
            String jsonPolicy = mapper.writeValueAsString(policy);

            // 将 JSON policy 按 UTF-8 转字节后进行 Base64 编码。
            String policyBase64 = Base64.getEncoder().encodeToString(jsonPolicy.getBytes(StandardCharsets.UTF_8));
            // 设置响应中的 Base64 policy。
            signDTO.setPolicy(policyBase64);

            // 使用 aliyun_v4 + AccessKeySecret 派生日期签名 key。
            byte[] dateKey = hmacsha256(("aliyun_v4" + accessKeySecret).getBytes(StandardCharsets.UTF_8), dateStr);
            // 使用日期 key 派生 region 签名 key。
            byte[] dateRegionKey = hmacsha256(dateKey, ossProperties.getRegion());
            // 使用 region key 派生 oss 服务签名 key。
            byte[] dateRegionServiceKey = hmacsha256(dateRegionKey, "oss");
            // 使用服务 key 派生最终 signing key。
            byte[] signingKey = hmacsha256(dateRegionServiceKey, "aliyun_v4_request");

            // 使用 signing key 对 Base64 policy 计算签名。
            byte[] result = hmacsha256(signingKey, policyBase64);
            // 将签名字节数组转换为十六进制字符串。
            String signature = BinaryUtil.toHex(result);
            // 设置响应中的 OSS V4 signature。
            signDTO.setSignature(signature);
            // 返回表单直传签名。
            return signDTO;
        } catch (Exception e) {
            // 记录签名生成失败的完整异常。
            log.error("生成 OSS 直传签名失败", e);
            // 对外返回统一的获取直传地址失败响应码。
            throw new ServiceException(ResultCode.PRE_SIGN_URL_FAILED);
        }
    }

    /**
     * 计算 HMAC-SHA256。
     *
     * @param key HMAC 密钥
     * @param data 待签名数据
     * @return HMAC-SHA256 结果
     */
    public static byte[] hmacsha256(byte[] key, String data) {
        // 捕获 HMAC 计算中的异常，统一转换成业务异常。
        try {
            // 初始化 HMAC 密钥规格，指定算法为 HmacSHA256。
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, "HmacSHA256");
            // 获取 HmacSHA256 Mac 实例。
            Mac mac = Mac.getInstance("HmacSHA256");
            // 使用密钥初始化 Mac 对象。
            mac.init(secretKeySpec);
            // 使用 UTF-8 字节执行 HMAC 计算。
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // 记录 HMAC 计算失败的完整异常。
            log.error("生成 OSS 直传签名失败", e);
            // 对外返回统一的获取直传地址失败响应码。
            throw new ServiceException(ResultCode.PRE_SIGN_URL_FAILED);
        }
    }

    /**
     * 生成存储文件名，保留原始扩展名。
     *
     * @param originalFilename 原始文件名
     * @return OSS 中使用的新文件名
     */
    private String buildFileName(String originalFilename) {
        // 从原始文件名中提取扩展名，例如 test.png 提取为 png。
        String extension = StringUtils.getFilenameExtension(originalFilename);
        // 生成 UUID，并去掉横杠，避免文件名过长或包含特殊分隔符。
        String uuid = UUID.randomUUID().toString().replace("-", "");
        // 判断原始文件名是否包含有效扩展名。
        if (!StringUtils.hasText(extension)) {
            // 没有扩展名时只使用 UUID 作为文件名。
            return uuid;
        }
        // 有扩展名时使用 UUID + 扩展名，降低文件重名风险。
        return uuid + "." + extension;
    }

    /**
     * 生成 OSS 对象名称。
     *
     * @param fileName 文件名
     * @return OSS 对象名称
     */
    private String buildObjectName(String fileName) {
        // 生成日期目录，例如 2026/08/29。
        String datePath = LocalDate.now().format(DATE_PATH_FORMATTER);
        // 规范化配置中的路径前缀。
        String prefix = normalizePathPrefix(ossProperties.getPathPrefix());
        // 拼接完整 objectName，例如 upload/2026/08/29/xxx.png。
        return prefix + datePath + "/" + fileName;
    }

    /**
     * 规范化配置中的路径前缀。
     *
     * @param pathPrefix 配置路径前缀
     * @return 以 / 结尾且不以 / 开头的路径前缀
     */
    private String normalizePathPrefix(String pathPrefix) {
        // 路径前缀为空时不拼接业务目录。
        if (!StringUtils.hasText(pathPrefix)) {
            // 返回空字符串，避免 objectName 出现 null。
            return "";
        }
        // 将 Windows 风格反斜杠统一替换为 OSS 路径使用的正斜杠。
        String normalized = pathPrefix.replace("\\", "/");
        // 循环去掉开头的斜杠，避免 objectName 以 / 开头。
        while (normalized.startsWith("/")) {
            // 删除当前最前面的一个斜杠。
            normalized = normalized.substring(1);
        }
        // 判断前缀是否已经以 / 结尾。
        if (!normalized.endsWith("/")) {
            // 追加结尾斜杠，方便后续直接拼接日期目录。
            normalized = normalized + "/";
        }
        // 返回规范化后的路径前缀。
        return normalized;
    }
}
