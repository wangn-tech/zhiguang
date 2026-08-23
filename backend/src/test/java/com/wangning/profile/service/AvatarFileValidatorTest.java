package com.wangning.profile.service;

import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.storage.aliyun.OssProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AvatarFileValidatorTest {

    private AvatarFileValidator validator;

    @BeforeEach
    void setUp() {
        OssProperties properties = new OssProperties();
        properties.setAvatarMaxSize(DataSize.ofMegabytes(5));
        validator = new AvatarFileValidator(properties);
    }

    @ParameterizedTest
    @MethodSource("supportedImages")
    void shouldAcceptSupportedImageSignatures(
            String contentType,
            String extension,
            byte[] content
    ) {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar." + extension,
                contentType,
                content
        );

        AvatarFileValidator.ValidatedAvatar result = validator.validate(file);

        assertThat(result.contentType()).isEqualTo(contentType);
        assertThat(result.extension()).isEqualTo(extension);
    }

    @Test
    void shouldRejectEmptyAndOversizedFiles() {
        MockMultipartFile empty = new MockMultipartFile(
                "file",
                "empty.png",
                "image/png",
                new byte[0]
        );
        MockMultipartFile oversized = new MockMultipartFile(
                "file",
                "large.png",
                "image/png",
                new byte[(int) DataSize.ofMegabytes(5).toBytes() + 1]
        );

        assertBadRequest(() -> validator.validate(empty), "头像文件不能为空");
        assertBadRequest(() -> validator.validate(oversized), "头像文件大小超过限制");
    }

    @Test
    void shouldRejectUnsupportedOrMismatchedImageTypes() {
        MockMultipartFile svg = new MockMultipartFile(
                "file",
                "avatar.svg",
                "image/svg+xml",
                "<svg></svg>".getBytes()
        );
        MockMultipartFile disguisedPng = new MockMultipartFile(
                "file",
                "avatar.jpg",
                "image/jpeg",
                pngBytes()
        );
        MockMultipartFile missingContentType = new MockMultipartFile(
                "file",
                "avatar.png",
                null,
                pngBytes()
        );

        assertErrorCode(() -> validator.validate(svg), ErrorCode.BAD_REQUEST);
        assertErrorCode(() -> validator.validate(disguisedPng), ErrorCode.BAD_REQUEST);
        assertErrorCode(() -> validator.validate(missingContentType), ErrorCode.BAD_REQUEST);
    }

    @Test
    void shouldConvertFileReadFailureToBadRequest() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(128L);
        when(file.getContentType()).thenReturn("image/png");
        when(file.getInputStream()).thenThrow(new IOException("read failed"));

        assertBadRequest(() -> validator.validate(file), "头像文件读取失败");
    }

    private static Stream<Arguments> supportedImages() {
        return Stream.of(
                Arguments.of("image/jpeg", "jpg", new byte[]{
                        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00
                }),
                Arguments.of("image/png", "png", pngBytes()),
                Arguments.of("image/webp", "webp", new byte[]{
                        'R', 'I', 'F', 'F', 0x04, 0x00, 0x00, 0x00,
                        'W', 'E', 'B', 'P'
                })
        );
    }

    private static byte[] pngBytes() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A,
                0x00
        };
    }

    private void assertBadRequest(Runnable action, String expectedMessage) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(exception.getMessage()).isEqualTo(expectedMessage);
                });
    }

    private void assertErrorCode(Runnable action, ErrorCode expectedErrorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode));
    }
}
