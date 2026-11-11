package com.anyujia.anyujiafileservice.service.impl;

import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import com.anyujia.anyujiafileservice.domain.dto.FileDTO;
import com.anyujia.anyujiafileservice.domain.dto.SignDTO;
import com.anyujia.anyujiafileservice.service.IOSSFileService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@ConditionalOnProperty(value = "storage.type", havingValue = "minio")
public class MinioFileServiceImpl implements IOSSFileService {
    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    @Value("${minio.endpoint}")
    private String endpoint;
    @Value("${minio.public-url}")
    private String publicUrl;
    @Value("${minio.access-key}")
    private String accessKey;
    @Value("${minio.secret-key}")
    private String secretKey;
    @Value("${minio.bucket:chat-images}")
    private String bucket;
    @Value("${minio.public-read:false}")
    private boolean publicRead;

    private MinioClient client;

    @PostConstruct
    public void initialize() {
        client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            if (publicRead) {
                // Local development only. Production buckets should stay private.
                String policy = """
                        {"Version":"2012-10-17","Statement":[{"Effect":"Allow",
                         "Principal":{"AWS":["*"]},"Action":["s3:GetObject"],
                         "Resource":["arn:aws:s3:::%s/*"]}]}
                        """.formatted(bucket);
                client.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize MinIO chat bucket", ex);
        }
    }

    @Override
    public FileDTO upload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ServiceException(ResultCode.FILE_EMPTY);
        String original = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(original);
        String suffix = extension != null && extension.matches("[a-zA-Z0-9]{1,10}")
                ? "." + extension.toLowerCase(java.util.Locale.ROOT) : "";
        String fileName = UUID.randomUUID().toString().replace("-", "") + suffix;
        String key = "chat/" + LocalDate.now().format(DATE_PATH) + "/" + fileName;
        try (InputStream stream = file.getInputStream()) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket).object(key)
                    .stream(stream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception ex) {
            log.error("MinIO upload failed, key={}", key, ex);
            throw new ServiceException(ResultCode.OSS_UPLOAD_FAILED);
        }
        FileDTO result = new FileDTO();
        result.setUrl(publicUrl.replaceAll("/+$", "") + "/" + bucket + "/" + key);
        result.setKey(key);
        result.setName(fileName);
        result.setBucketName(bucket);
        result.setObjectName(key);
        result.setFileName(fileName);
        result.setOriginalFilename(original);
        result.setContentType(file.getContentType());
        result.setSize(file.getSize());
        return result;
    }

    @Override
    public SignDTO getSign() {
        throw new ServiceException(ResultCode.PRE_SIGN_URL_FAILED);
    }
}
