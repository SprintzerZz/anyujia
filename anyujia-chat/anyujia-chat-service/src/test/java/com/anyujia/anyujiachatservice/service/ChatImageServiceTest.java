package com.anyujia.anyujiachatservice.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import com.anyujia.anyujiafileapi.feign.FileFeignClient;
import com.anyujia.anyujiachatapi.domain.vo.ChatImageVO;
import com.anyujia.anyujiafileapi.domain.vo.FileVO;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class ChatImageServiceTest {
    private static final String UPLOAD_ID = "bb2af26d-6633-4b9e-8541-349ad6337616";
    private final ConversationMemberService members = mock(ConversationMemberService.class);
    private final FileFeignClient files = mock(FileFeignClient.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ChatImageService service = new ChatImageService(members, files, jdbc);

    @Test
    void rejectsNonImageBeforeUploading() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(12L))).thenReturn(1);
        var file = new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes());

        assertThatThrownBy(() -> service.upload(7L, 12L, UPLOAD_ID, file)).isInstanceOf(ServiceException.class);

        verify(members).requireMember(12L, 7L);
        verifyNoInteractions(files);
    }

    @Test
    void uploadsValidatedImageAndRecordsItsOwner() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(12L))).thenReturn(1);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), "png", bytes);
        var file = new MockMultipartFile("file", "photo.png", "image/png", bytes.toByteArray());
        FileVO stored = new FileVO();
        stored.setKey("chat/photo.png");
        stored.setUrl("https://example.test/chat/photo.png");
        when(files.upload(file)).thenReturn(ApiResult.success(stored));

        service.upload(7L, 12L, UPLOAD_ID, file);

        verify(files).upload(file);
        verify(jdbc).update(anyString(), any(Object[].class));
    }

    @Test
    void rejectsThirdConcurrentUploadBeforeDecodingOrCallingStorage() throws Exception {
        ReflectionTestUtils.setField(service, "localUploadWaitMillis", 100L);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(12L))).thenReturn(1);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), "png", bytes);
        var file = new MockMultipartFile("file", "photo.png", "image/png", bytes.toByteArray());
        FileVO stored = new FileVO();
        stored.setKey("chat/photo.png");
        stored.setUrl("https://example.test/chat/photo.png");
        CountDownLatch uploading = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        when(files.upload(file)).thenAnswer(invocation -> {
            uploading.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
            return ApiResult.success(stored);
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> service.upload(7L, 12L, UPLOAD_ID, file));
            var second = executor.submit(() -> service.upload(7L, 12L, UPLOAD_ID, file));
            try {
                org.assertj.core.api.Assertions.assertThat(uploading.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> service.upload(7L, 12L, UPLOAD_ID, file))
                        .isInstanceOf(ServiceException.class)
                        .hasMessageContaining("Too many image uploads");
            } finally {
                release.countDown();
            }
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void waitsForAnUploadSlotAndCompletesWhenOneIsReleased() throws Exception {
        ReflectionTestUtils.setField(service, "localUploadWaitMillis", 1000L);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(12L))).thenReturn(1);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), "png", bytes);
        var file = new MockMultipartFile("file", "photo.png", "image/png", bytes.toByteArray());
        FileVO stored = new FileVO();
        stored.setKey("chat/photo.png");
        stored.setUrl("https://example.test/chat/photo.png");
        CountDownLatch uploading = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        when(files.upload(file)).thenAnswer(invocation -> {
            uploading.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
            return ApiResult.success(stored);
        });
        var executor = Executors.newFixedThreadPool(3);
        try {
            var first = executor.submit(() -> service.upload(7L, 12L, UUID.randomUUID().toString(), file));
            var second = executor.submit(() -> service.upload(7L, 12L, UUID.randomUUID().toString(), file));
            assertThat(uploading.await(5, TimeUnit.SECONDS)).isTrue();
            var queued = executor.submit(() -> service.upload(7L, 12L, UUID.randomUUID().toString(), file));
            Thread.sleep(50);
            assertThat(queued.isDone()).isFalse();
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            queued.get(5, TimeUnit.SECONDS);
            verify(files, times(3)).upload(file);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsImageBelongingToAnotherConversationOrUser() {
        String assetId = "bb2af26d-6633-4b9e-8541-349ad6337616";
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(assetId), eq(12L), eq(7L)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.requireOwnedImage(assetId, 12L, 7L))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void repeatedUploadIdReturnsExistingAssetWithoutUploadingAgain() {
        ChatImageVO image = new ChatImageVO();
        image.setAssetId("asset-1");
        when(jdbc.query(argThat(sql -> sql.contains("WHERE owner_id")), any(org.springframework.jdbc.core.RowMapper.class),
                eq(7L), eq(12L), eq(UPLOAD_ID))).thenReturn(List.of("asset-1"));
        when(jdbc.query(argThat(sql -> sql.contains("SELECT asset_id, url")),
                any(org.springframework.jdbc.core.RowMapper.class), eq("asset-1"))).thenReturn(List.of(image));

        assertThat(service.upload(7L, 12L, UPLOAD_ID, null)).isSameAs(image);

        verifyNoInteractions(files);
    }
}
