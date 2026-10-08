package com.anyujia.anyujialegalservice.integration.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anyujia.anyujiafileapi.domain.vo.FileVO;
import com.anyujia.anyujiafileapi.feign.FileFeignClient;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class FileServiceContractArchiveTest {
    @Test
    void returnsPrivateObjectKeyFromFileService() {
        FileFeignClient client = mock(FileFeignClient.class);
        MockMultipartFile file = new MockMultipartFile(
                "file", "contract.pdf", "application/pdf", "%PDF".getBytes());
        FileVO uploaded = new FileVO();
        uploaded.setKey("contracts/2026/10/03/private.pdf");
        when(client.upload(file)).thenReturn(ApiResult.success(uploaded));

        String objectKey = new FileServiceContractArchive(client).archive(file);

        assertThat(objectKey).isEqualTo("contracts/2026/10/03/private.pdf");
    }

    @Test
    void rejectsSuccessfulResponseWithoutObjectKey() {
        FileFeignClient client = mock(FileFeignClient.class);
        MockMultipartFile file = new MockMultipartFile(
                "file", "contract.pdf", "application/pdf", "%PDF".getBytes());
        when(client.upload(file)).thenReturn(ApiResult.success(new FileVO()));

        assertThatThrownBy(() -> new FileServiceContractArchive(client).archive(file))
                .isInstanceOf(ServiceException.class)
                .hasMessage("合同原件归档到 OSS 失败");
    }
}
