package com.cinoo.matchmateserver.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.model.ObjectMetadata;
import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.config.OssProperties;
import com.cinoo.matchmateserver.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;

@Component
@Slf4j
public class OssUtils {

    private static final long MAX_AVATAR_SIZE = 5 * 1024 * 1024;
    private static final String AVATAR_DIRECTORY = "userAvatar";

    private final OssProperties properties;
    private volatile OSS client;

    public OssUtils(OssProperties properties) {
        this.properties = properties;
    }

    public String uploadAvatar(long userId, MultipartFile file) {
        byte[] content = readAndValidate(file);
        ImageType imageType = detectImageType(content);
        String objectKey = buildAvatarObjectKey(userId, imageType.extension);

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(content.length);
        metadata.setContentType(imageType.contentType);
        metadata.setCacheControl("public, max-age=31536000, immutable");

        try {
            getClient().putObject(
                    properties.getBucketName(),
                    objectKey,
                    new ByteArrayInputStream(content),
                    metadata
            );
            return publicBaseUrl() + objectKey;
        } catch (RuntimeException e) {
            log.error("Failed to upload avatar to OSS, userId={}", userId, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "头像上传失败，请稍后重试");
        }
    }

    public void deleteIfManaged(String url) {
        try {
            String objectKey = managedObjectKey(url);
            if (objectKey == null) {
                return;
            }
            getClient().deleteObject(properties.getBucketName(), objectKey);
        } catch (RuntimeException e) {
            log.warn("Failed to delete managed OSS object: url={}", url, e);
        }
    }

    private byte[] readAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "头像文件不能为空");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED, "头像大小不能超过 5MB");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "头像文件读取失败");
        }
    }

    private ImageType detectImageType(byte[] content) {
        if (hasSignature(content, 0xFF, 0xD8, 0xFF)) {
            return ImageType.JPEG;
        }
        if (hasSignature(content, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return ImageType.PNG;
        }
        if (hasSignature(content, 'G', 'I', 'F', '8', '7', 'a')
                || hasSignature(content, 'G', 'I', 'F', '8', '9', 'a')) {
            return ImageType.GIF;
        }
        if (hasSignature(content, 'R', 'I', 'F', 'F')
                && content.length >= 12
                && hasSignatureAt(content, 8, 'W', 'E', 'B', 'P')) {
            return ImageType.WEBP;
        }
        throw new BusinessException(
                ErrorCode.FILE_TYPE_ERROR,
                "仅支持 JPG、PNG、GIF、WebP 图片"
        );
    }

    private boolean hasSignature(byte[] content, int... signature) {
        return hasSignatureAt(content, 0, signature);
    }

    private boolean hasSignatureAt(byte[] content, int offset, int... signature) {
        if (content.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((content[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    static String buildAvatarObjectKey(long userId, String extension) {
        return "%s/%d/%s.%s".formatted(
                AVATAR_DIRECTORY,
                userId,
                UUID.randomUUID(),
                extension
        );
    }

    private String managedObjectKey(String url) {
        if (StringUtils.isBlank(url)) {
            return null;
        }
        String baseUrl = publicBaseUrl();
        if (!url.startsWith(baseUrl)) {
            return null;
        }
        String objectKey = url.substring(baseUrl.length());
        return objectKey.isBlank() ? null : objectKey;
    }

    private OSS getClient() {
        validateConfiguration();
        OSS currentClient = client;
        if (currentClient == null) {
            synchronized (this) {
                currentClient = client;
                if (currentClient == null) {
                    currentClient = OSSClientBuilder.create()
                            .endpoint(properties.getEndpoint())
                            .credentialsProvider(
                                    CredentialsProviderFactory.newDefaultCredentialProvider(
                                            properties.getAccessKeyId(),
                                            properties.getAccessKeySecret()
                                    )
                            )
                            .build();
                    client = currentClient;
                }
            }
        }
        return currentClient;
    }

    private String publicBaseUrl() {
        if (StringUtils.isNotBlank(properties.getPublicBaseUrl())) {
            String configuredUrl = StringUtils.prependIfMissing(
                    properties.getPublicBaseUrl().trim(),
                    "https://"
            );
            return StringUtils.appendIfMissing(configuredUrl, "/");
        }
        validateConfiguration();
        String endpointHost = URI.create(
                StringUtils.prependIfMissing(properties.getEndpoint().trim(), "https://")
        ).getHost();
        if (endpointHost == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "OSS endpoint 配置错误");
        }
        return "https://%s.%s/".formatted(properties.getBucketName(), endpointHost);
    }

    private void validateConfiguration() {
        if (StringUtils.isAnyBlank(
                properties.getEndpoint(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret(),
                properties.getBucketName())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "OSS 配置不完整");
        }
    }

    @PreDestroy
    public void shutdown() {
        OSS currentClient = client;
        if (currentClient != null) {
            currentClient.shutdown();
        }
    }

    private enum ImageType {
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png"),
        GIF("gif", "image/gif"),
        WEBP("webp", "image/webp");

        private final String extension;
        private final String contentType;

        ImageType(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }
    }
}
