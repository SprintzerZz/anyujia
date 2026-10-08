package com.anyujia.anyujialegalservice.service;

import org.springframework.web.multipart.MultipartFile;

public interface ContractArchiveService {
    String archive(MultipartFile file);
}
