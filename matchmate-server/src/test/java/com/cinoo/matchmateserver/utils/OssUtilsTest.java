package com.cinoo.matchmateserver.utils;

import com.cinoo.matchmateserver.common.ErrorCode;
import com.cinoo.matchmateserver.config.OssProperties;
import com.cinoo.matchmateserver.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OssUtilsTest {

    private final OssUtils ossUtils = new OssUtils(new OssProperties());

    @Test
    void rejectsFileWhoseContentIsNotAnImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.jpg",
                "image/jpeg",
                "not an image".getBytes()
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ossUtils.uploadAvatar(1L, file)
        );

        assertEquals(ErrorCode.FILE_TYPE_ERROR.getCode(), exception.getCode());
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.png",
                "image/png",
                new byte[0]
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ossUtils.uploadAvatar(1L, file)
        );

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    @Test
    void avatarObjectKeyUsesConfiguredDirectory() {
        String objectKey = OssUtils.buildAvatarObjectKey(7L, "png");

        assertTrue(objectKey.startsWith("userAvatar/7/"));
        assertTrue(objectKey.endsWith(".png"));
    }
}
