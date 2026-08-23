package com.wangning.storage.aliyun;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.PresignOptions;
import com.aliyun.sdk.service.oss2.exceptions.OperationException;
import com.aliyun.sdk.service.oss2.models.DeleteObjectRequest;
import com.aliyun.sdk.service.oss2.models.PresignResult;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.models.PutObjectResult;
import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.storage.model.PresignedUpload;
import com.wangning.storage.model.StoredObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AliyunOssStorageServiceTest {

    @Mock
    private OSSClient ossClient;

    private OssProperties properties;
    private AliyunOssStorageService storageService;

    @BeforeEach
    void setUp() {
        properties = new OssProperties();
        properties.setEnabled(true);
        properties.setRegion("cn-beijing");
        properties.setEndpoint("https://oss-cn-beijing.aliyuncs.com");
        properties.setBucket("zhiguang-test");
        properties.setPublicBaseUrl("https://static.example.com/");
        storageService = new AliyunOssStorageService(ossClient, properties);
    }

    @Test
    void shouldUploadObjectAndReturnStorageMetadata() {
        byte[] content = "avatar-content".getBytes(StandardCharsets.UTF_8);
        PutObjectResult sdkResult = mock(PutObjectResult.class);
        when(sdkResult.eTag()).thenReturn("avatar-etag");
        when(ossClient.putObject(any(PutObjectRequest.class))).thenReturn(sdkResult);

        StoredObject result = storageService.upload(
                "avatars/42/avatar.png",
                "image/png",
                content.length,
                new ByteArrayInputStream(content)
        );

        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(ossClient).putObject(requestCaptor.capture());
        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo("zhiguang-test");
        assertThat(request.key()).isEqualTo("avatars/42/avatar.png");
        assertThat(request.contentType()).isEqualTo("image/png");
        assertThat(request.contentLength()).isEqualTo(content.length);
        assertThat(request.body().toBytes()).isEqualTo(content);

        assertThat(result.objectKey()).isEqualTo("avatars/42/avatar.png");
        assertThat(result.publicUrl())
                .isEqualTo("https://static.example.com/avatars/42/avatar.png");
        assertThat(result.eTag()).isEqualTo("avatar-etag");
    }

    @Test
    void shouldGenerateEncodedDefaultPublicUrl() {
        properties.setPublicBaseUrl(null);

        String result = storageService.publicUrl("posts/1001/课程 封面.png");

        assertThat(result).isEqualTo(
                "https://zhiguang-test.oss-cn-beijing.aliyuncs.com/"
                        + "posts/1001/%E8%AF%BE%E7%A8%8B%20%E5%B0%81%E9%9D%A2.png"
        );
    }

    @Test
    void shouldDeleteObjectFromConfiguredBucket() {
        storageService.delete("avatars/42/old.png");

        ArgumentCaptor<DeleteObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(ossClient).deleteObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("zhiguang-test");
        assertThat(requestCaptor.getValue().key()).isEqualTo("avatars/42/old.png");
    }

    @Test
    void shouldGeneratePutPresignWithRequiredHeadersAndTtl() {
        PresignResult sdkResult = PresignResult.newBuilder()
                .url("https://signed.example.com/upload?signature=masked")
                .method("PUT")
                .expiration(Instant.now().plus(Duration.ofMinutes(5)))
                .signedHeaders(Map.of("content-type", "image/webp", "x-oss-test", "signed"))
                .build();
        when(ossClient.presign(any(PutObjectRequest.class), any(PresignOptions.class)))
                .thenReturn(sdkResult);

        PresignedUpload result = storageService.presignUpload(
                "posts/1001/images/image.webp",
                "image/webp",
                Duration.ofMinutes(5)
        );

        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<PresignOptions> optionsCaptor =
                ArgumentCaptor.forClass(PresignOptions.class);
        verify(ossClient).presign(requestCaptor.capture(), optionsCaptor.capture());
        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo("zhiguang-test");
        assertThat(request.key()).isEqualTo("posts/1001/images/image.webp");
        assertThat(request.contentType()).isEqualTo("image/webp");
        assertThat(optionsCaptor.getValue().expiration()).isPresent();

        assertThat(result.objectKey()).isEqualTo("posts/1001/images/image.webp");
        assertThat(result.putUrl()).isEqualTo("https://signed.example.com/upload?signature=masked");
        assertThat(result.headers())
                .containsEntry("content-type", "image/webp")
                .containsEntry("x-oss-test", "signed");
        assertThat(result.expiresIn()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void shouldAddContentTypeWhenSdkDoesNotReturnSignedHeaders() {
        PresignResult sdkResult = PresignResult.newBuilder()
                .url("https://signed.example.com/upload?signature=masked")
                .method("PUT")
                .build();
        when(ossClient.presign(any(PutObjectRequest.class), any(PresignOptions.class)))
                .thenReturn(sdkResult);

        PresignedUpload result = storageService.presignUpload(
                "posts/1001/content/content.md",
                "text/markdown",
                Duration.ofMinutes(10)
        );

        assertThat(result.headers()).containsExactlyEntriesOf(
                Map.of("Content-Type", "text/markdown")
        );
    }

    @Test
    void shouldRejectUnsafeObjectKeyBeforeCallingSdk() {
        assertThatThrownBy(() -> storageService.delete("../secret.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("objectKey 格式非法");

        verify(ossClient, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void shouldRejectInvalidUploadArgumentsBeforeCallingSdk() {
        assertThatThrownBy(() -> storageService.upload(
                "avatars/42/avatar.png",
                " ",
                1,
                new ByteArrayInputStream(new byte[]{1})
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("contentType 不能为空");

        assertThatThrownBy(() -> storageService.presignUpload(
                "posts/1001/content/content.md",
                "text/markdown",
                Duration.ZERO
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ttl 必须大于 0");

        verify(ossClient, never()).putObject(any(PutObjectRequest.class));
        verify(ossClient, never()).presign(any(PutObjectRequest.class), any(PresignOptions.class));
    }

    @Test
    void shouldConvertSdkFailureToStableStorageError() {
        when(ossClient.putObject(any(PutObjectRequest.class)))
                .thenThrow(new OperationException("PutObject"));

        assertThatThrownBy(() -> storageService.upload(
                "avatars/42/avatar.png",
                "image/png",
                1,
                new ByteArrayInputStream(new byte[]{1})
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STORAGE_OPERATION_FAILED);
                    assertThat(exception.getMessage()).isEqualTo("对象存储操作失败");
                });
    }

    @Test
    void shouldRejectMalformedEndpointAsConfigurationError() {
        properties.setPublicBaseUrl(null);
        properties.setEndpoint("https://bad host");

        assertThatThrownBy(() -> storageService.publicUrl("avatars/42/avatar.png"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.STORAGE_CONFIGURATION_ERROR));
    }
}
