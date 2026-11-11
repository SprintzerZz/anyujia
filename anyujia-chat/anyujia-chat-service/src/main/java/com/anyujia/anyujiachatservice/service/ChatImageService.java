package com.anyujia.anyujiachatservice.service;

import com.anyujia.anyujiachatapi.domain.vo.ChatImageVO;
import com.anyujia.anyujiafileapi.domain.vo.FileVO;
import com.anyujia.anyujiafileapi.feign.FileFeignClient;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ChatImageService {
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final int MAX_DIMENSION = 10000;
    // ImageIO decodes images into heap memory. Bound simultaneous decodes and OSS uploads per node.
    private final Semaphore uploadSlots = new Semaphore(2, true);
    private final AtomicInteger uploadWaiters = new AtomicInteger();

    @Value("${chat.image.local-upload-wait-millis:2000}")
    private long localUploadWaitMillis = 2000;

    @Value("${chat.image.local-max-waiters:16}")
    private int localMaxWaiters = 16;

    private final ConversationMemberService memberService;
    private final FileFeignClient fileClient;
    private final JdbcTemplate jdbcTemplate;

    private final ChatImageUploadCoordinator uploadCoordinator;

    @Autowired
    public ChatImageService(ConversationMemberService memberService, FileFeignClient fileClient,
                            JdbcTemplate jdbcTemplate, ChatImageUploadCoordinator uploadCoordinator) {
        this.memberService = memberService;
        this.fileClient = fileClient;
        this.jdbcTemplate = jdbcTemplate;
        this.uploadCoordinator = uploadCoordinator;
    }

    /** Lightweight constructor for service tests without Redis. */
    ChatImageService(ConversationMemberService memberService, FileFeignClient fileClient,
                     JdbcTemplate jdbcTemplate) {
        this(memberService, fileClient, jdbcTemplate, null);
    }

    public ChatImageVO upload(Long userId, Long conversationId, String uploadId, MultipartFile file) {
        memberService.requireMember(conversationId, userId);
        if (uploadId == null || !uploadId.matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw invalid("Invalid upload ID");
        }
        if (uploadCoordinator != null) {
            return uploadCoordinator.withUploadLock(userId, conversationId, uploadId,
                    () -> uploadLocked(userId, conversationId, uploadId, file));
        }
        return uploadLocked(userId, conversationId, uploadId, file);
    }

    private ChatImageVO uploadLocked(Long userId, Long conversationId, String uploadId, MultipartFile file) {
        ChatImageVO existing = findByUploadId(userId, conversationId, uploadId);
        if (existing != null) return existing;
        boolean acquired;
        try {
            acquired = uploadSlots.tryAcquire(0, TimeUnit.MILLISECONDS);
            if (!acquired) {
                int waiting = uploadWaiters.incrementAndGet();
                try {
                    if (waiting > Math.max(0, localMaxWaiters)) throw busy();
                    acquired = uploadSlots.tryAcquire(Math.max(0, localUploadWaitMillis), TimeUnit.MILLISECONDS);
                } finally {
                    uploadWaiters.decrementAndGet();
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw busy();
        }
        if (!acquired) throw busy();
        try {
            if (uploadCoordinator != null) {
                return uploadCoordinator.withGlobalPermit(
                        () -> uploadWithinSlot(userId, conversationId, uploadId, file));
            }
            return uploadWithinSlot(userId, conversationId, uploadId, file);
        } finally {
            uploadSlots.release();
        }
    }

    private ChatImageVO uploadWithinSlot(Long userId, Long conversationId, String uploadId, MultipartFile file) {
        Integer conversationType = jdbcTemplate.queryForObject(
                "SELECT conversation_type FROM chat_conversation WHERE id = ?", Integer.class, conversationId);
        if (conversationType == null || conversationType != 1) {
            throw invalid("Image messages are only supported in tenant-landlord conversations");
        }
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw invalid("Image must be between 1 byte and 10 MB");
        }
        String format;
        int width;
        int height;
        try (ImageInputStream input = ImageIO.createImageInputStream(file.getInputStream())) {
            if (input == null) throw invalid("Invalid image");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid("Unsupported image format");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                width = reader.getWidth(0);
                height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > 20_000_000L) {
                    throw invalid("Invalid image dimensions");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) throw invalid("Invalid image");
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            throw invalid("Invalid image");
        }
        String contentType = switch (format) {
            case "jpeg", "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            default -> throw invalid("Only JPEG, PNG and GIF images are supported");
        };
        if (!contentType.equalsIgnoreCase(file.getContentType())) {
            throw invalid("Image content type does not match its format");
        }
        String originalName = file.getOriginalFilename();
        String extension = org.springframework.util.StringUtils.getFilenameExtension(originalName);
        boolean extensionMatches = extension != null && switch (contentType) {
            case "image/jpeg" -> extension.equalsIgnoreCase("jpg") || extension.equalsIgnoreCase("jpeg");
            case "image/png" -> extension.equalsIgnoreCase("png");
            default -> extension.equalsIgnoreCase("gif");
        };
        if (!extensionMatches) {
            throw invalid("Image filename does not match its format");
        }
        ApiResult<FileVO> uploaded = fileClient.upload(file);
        FileVO stored = uploaded == null || uploaded.getCode() != ResultCode.SUCCESS.getCode()
                ? null : uploaded.getData();
        if (stored == null || stored.getKey() == null || stored.getUrl() == null) {
            throw new ServiceException("Image storage failed");
        }
        String assetId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO chat_image_asset
                  (asset_id, conversation_id, owner_id, upload_id, object_key, url, content_type, size_bytes, width, height)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, assetId, conversationId, userId, uploadId, stored.getKey(), stored.getUrl(),
                contentType, file.getSize(), width, height);
        return image(assetId);
    }

    private ChatImageVO findByUploadId(Long userId, Long conversationId, String uploadId) {
        var ids = jdbcTemplate.query("""
                SELECT asset_id FROM chat_image_asset
                 WHERE owner_id = ? AND conversation_id = ? AND upload_id = ?
                """, (rs, rowNum) -> rs.getString(1), userId, conversationId, uploadId);
        return ids.isEmpty() ? null : image(ids.get(0));
    }

    public void requireOwnedImage(String assetId, Long conversationId, Long userId) {
        if (assetId == null || !assetId.matches("[0-9a-fA-F-]{36}")) {
            throw invalid("Invalid image asset ID");
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM chat_image_asset
                 WHERE asset_id = ? AND conversation_id = ? AND owner_id = ?
                """, Integer.class, assetId, conversationId, userId);
        if (count == null || count != 1) throw invalid("Image is not owned by this conversation member");
    }

    public ChatImageVO image(String assetId) {
        var rows = jdbcTemplate.query("""
                SELECT asset_id, url, content_type, size_bytes, width, height
                  FROM chat_image_asset WHERE asset_id = ?
                """, (rs, rowNum) -> {
            ChatImageVO image = new ChatImageVO();
            image.setAssetId(rs.getString("asset_id"));
            image.setUrl(rs.getString("url"));
            image.setContentType(rs.getString("content_type"));
            image.setSize(rs.getLong("size_bytes"));
            image.setWidth(rs.getInt("width"));
            image.setHeight(rs.getInt("height"));
            return image;
        }, assetId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private ServiceException invalid(String message) {
        return new ServiceException(message, ResultCode.INVALID_PARA.getCode());
    }

    private ServiceException busy() {
        return new ServiceException("Too many image uploads; retry shortly",
                ResultCode.TOO_MANY_REQUESTS.getCode());
    }
}
