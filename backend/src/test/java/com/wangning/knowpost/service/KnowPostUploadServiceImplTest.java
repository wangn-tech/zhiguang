package com.wangning.knowpost.service;

import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.knowpost.api.dto.StoragePresignRequest;
import com.wangning.knowpost.domain.KnowPost;
import com.wangning.knowpost.mapper.KnowPostMapper;
import com.wangning.knowpost.service.impl.KnowPostUploadServiceImpl;
import com.wangning.storage.ObjectStorageService;
import com.wangning.storage.aliyun.OssProperties;
import com.wangning.storage.model.PresignedUpload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowPostUploadServiceImplTest {

    @Mock
    private KnowPostMapper knowPostMapper;

    @Mock
    private ObjectProvider<ObjectStorageService> objectStorageServiceProvider;

    @Mock
    private ObjectStorageService objectStorageService;

    private KnowPostUploadService knowPostUploadService;

    @BeforeEach
    void setUp() {
        OssProperties ossProperties = new OssProperties();
        ossProperties.setPresignTtl(Duration.ofMinutes(10));
        knowPostUploadService = new KnowPostUploadServiceImpl(
                knowPostMapper,
                objectStorageServiceProvider,
                ossProperties
        );
    }

    @Test
    void shouldCreateContentPresignUsingLegacyObjectKey() {
        when(knowPostMapper.findById(100L)).thenReturn(post(100L, 1L));
        when(objectStorageServiceProvider.getIfAvailable()).thenReturn(objectStorageService);
        when(objectStorageService.presignUpload(anyString(), anyString(), any(Duration.class)))
                .thenReturn(new PresignedUpload(
                        "posts/100/content.md",
                        "https://oss.example.com/put",
                        java.util.Map.of("Content-Type", "text/markdown"),
                        Duration.ofMinutes(10)
                ));

        var response = knowPostUploadService.presignUpload(
                1L,
                new StoragePresignRequest("knowpost_content", "100", "text/markdown", ".md")
        );

        assertThat(response.objectKey()).isEqualTo("posts/100/content.md");
        assertThat(response.expiresIn()).isEqualTo(600);
        verify(objectStorageService).presignUpload(
                "posts/100/content.md",
                "text/markdown",
                Duration.ofMinutes(10)
        );
    }

    @Test
    void shouldCreateImageObjectKeyWhenOwnerRequestsPresign() {
        when(knowPostMapper.findById(100L)).thenReturn(post(100L, 1L));
        when(objectStorageServiceProvider.getIfAvailable()).thenReturn(objectStorageService);
        when(objectStorageService.presignUpload(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(invocation -> new PresignedUpload(
                        invocation.getArgument(0),
                        "https://oss.example.com/put",
                        java.util.Map.of(),
                        Duration.ofSeconds(60)
                ));

        var response = knowPostUploadService.presignUpload(
                1L,
                new StoragePresignRequest("knowpost_image", "100", "image/png", "png")
        );

        assertThat(response.objectKey()).matches("posts/100/images/\\d{8}/[a-z0-9]{8}\\.png");
        assertThat(response.expiresIn()).isEqualTo(60);
    }

    @Test
    void shouldRejectOtherUsersAndUnknownScenes() {
        when(knowPostMapper.findById(100L)).thenReturn(post(100L, 2L));

        assertErrorCode(
                () -> knowPostUploadService.presignUpload(
                        1L,
                        new StoragePresignRequest("knowpost_image", "100", "image/png", ".png")
                ),
                ErrorCode.BAD_REQUEST
        );

        when(knowPostMapper.findById(100L)).thenReturn(post(100L, 1L));
        assertErrorCode(
                () -> knowPostUploadService.presignUpload(
                        1L,
                        new StoragePresignRequest("other", "100", "image/png", ".png")
                ),
                ErrorCode.BAD_REQUEST
        );
        verify(objectStorageServiceProvider, never()).getIfAvailable();
    }

    @Test
    void shouldReportStorageConfigurationErrorWhenOssDisabled() {
        when(knowPostMapper.findById(100L)).thenReturn(post(100L, 1L));
        when(objectStorageServiceProvider.getIfAvailable()).thenReturn(null);

        assertErrorCode(
                () -> knowPostUploadService.presignUpload(
                        1L,
                        new StoragePresignRequest("knowpost_image", "100", "image/png", ".png")
                ),
                ErrorCode.STORAGE_CONFIGURATION_ERROR
        );
    }

    private KnowPost post(long id, long creatorId) {
        return KnowPost.builder().id(id).creatorId(creatorId).build();
    }

    private void assertErrorCode(Runnable runnable, ErrorCode errorCode) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }
}
