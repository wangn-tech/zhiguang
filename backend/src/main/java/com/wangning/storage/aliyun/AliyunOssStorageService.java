package com.wangning.storage.aliyun;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.PresignOptions;
import com.aliyun.sdk.service.oss2.exceptions.OperationException;
import com.aliyun.sdk.service.oss2.exceptions.ServiceException;
import com.aliyun.sdk.service.oss2.models.DeleteObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectResult;
import com.aliyun.sdk.service.oss2.models.PresignResult;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.models.PutObjectResult;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.storage.ObjectStorageService;
import com.wangning.storage.model.PresignedUpload;
import com.wangning.storage.model.StoredObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.InputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 使用阿里云 OSS Java SDK V2 的对象存储实现。
 */
@Slf4j
@RequiredArgsConstructor
public class AliyunOssStorageService implements ObjectStorageService {

    private static final long MAX_SINGLE_UPLOAD_SIZE = Integer.MAX_VALUE;

    private final OSSClient ossClient;
    private final OssProperties properties;

    /**
     * {@inheritDoc}
     */
    @Override
    public StoredObject upload(
            String objectKey,
            String contentType,
            long contentLength,
            InputStream inputStream
    ) {
        validateObjectKey(objectKey);
        requireText(contentType, "contentType 不能为空");
        Objects.requireNonNull(inputStream, "inputStream 不能为空");
        if (contentLength < 0 || contentLength > MAX_SINGLE_UPLOAD_SIZE) {
            throw new IllegalArgumentException("contentLength 超出支持范围");
        }

        PutObjectRequest request = PutObjectRequest.newBuilder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(Math.toIntExact(contentLength))
                .body(BinaryData.fromStream(inputStream, contentLength))
                .build();
        String objectPublicUrl = publicUrl(objectKey);
        try {
            PutObjectResult result = ossClient.putObject(request);
            return new StoredObject(objectKey, objectPublicUrl, result.eTag());
        } catch (RuntimeException exception) {
            throw storageFailure("upload", objectKey, exception);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(String objectKey) {
        validateObjectKey(objectKey);
        DeleteObjectRequest request = DeleteObjectRequest.newBuilder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .build();
        try {
            ossClient.deleteObject(request);
        } catch (RuntimeException exception) {
            throw storageFailure("delete", objectKey, exception);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream download(String objectKey) {
        validateObjectKey(objectKey);
        GetObjectRequest request = GetObjectRequest.newBuilder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .build();
        try {
            GetObjectResult result = ossClient.getObject(request);
            InputStream body = result.body();
            if (body == null) {
                closeGetObjectResult(result);
                throw new IllegalStateException("OSS 返回了空对象内容流");
            }
            return new FilterInputStream(body) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        closeGetObjectResult(result);
                    }
                }
            };
        } catch (RuntimeException exception) {
            throw storageFailure("download", objectKey, exception);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PresignedUpload presignUpload(String objectKey, String contentType, Duration ttl) {
        validateObjectKey(objectKey);
        requireText(contentType, "contentType 不能为空");
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl 必须大于 0");
        }

        PutObjectRequest request = PutObjectRequest.newBuilder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .contentType(contentType)
                .build();
        PresignOptions options = PresignOptions.newBuilder()
                .expiration(ttl)
                .build();
        try {
            PresignResult result = ossClient.presign(request, options);
            return new PresignedUpload(
                    objectKey,
                    result.url(),
                    signedHeaders(result, contentType),
                    ttl
            );
        } catch (RuntimeException exception) {
            throw storageFailure("presign", objectKey, exception);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String publicUrl(String objectKey) {
        validateObjectKey(objectKey);
        String baseUrl = resolvePublicBaseUrl();
        String[] pathSegments = objectKey.split("/");
        return UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment(pathSegments)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
    }

    /**
     * 合并 SDK 返回的签名请求头，并确保调用方使用签名时的 Content-Type。
     *
     * @param result SDK 预签名结果
     * @param contentType 上传 MIME 类型
     * @return 不可变请求头
     */
    private Map<String, String> signedHeaders(PresignResult result, String contentType) {
        Map<String, String> headers = new LinkedHashMap<>();
        result.signedHeaders().ifPresent(headers::putAll);
        boolean containsContentType = headers.keySet().stream()
                .anyMatch(name -> "Content-Type".equalsIgnoreCase(name));
        if (!containsContentType) {
            headers.put("Content-Type", contentType);
        }
        return Map.copyOf(headers);
    }

    /**
     * 解析自定义域名或 OSS 默认公开域名。
     *
     * @return 不带末尾斜杠的基础地址
     */
    private String resolvePublicBaseUrl() {
        if (properties.getPublicBaseUrl() != null && !properties.getPublicBaseUrl().isBlank()) {
            return stripTrailingSlash(properties.getPublicBaseUrl().trim());
        }

        String endpoint = properties.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "https://oss-" + properties.getRegion() + ".aliyuncs.com";
        } else if (!endpoint.contains("://")) {
            endpoint = "https://" + endpoint;
        }

        URI endpointUri;
        try {
            endpointUri = URI.create(endpoint);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.STORAGE_CONFIGURATION_ERROR);
        }
        if (endpointUri.getScheme() == null || endpointUri.getHost() == null) {
            throw new BusinessException(ErrorCode.STORAGE_CONFIGURATION_ERROR);
        }

        String authority = properties.getBucket() + "." + endpointUri.getHost();
        if (endpointUri.getPort() >= 0) {
            authority += ":" + endpointUri.getPort();
        }
        return endpointUri.getScheme() + "://" + authority;
    }

    /**
     * 将 SDK 异常转换成稳定错误码，同时只记录安全的诊断信息。
     *
     * @param operation OSS 操作名称
     * @param objectKey 对象键
     * @param exception SDK 异常
     * @return 统一业务异常
     */
    private BusinessException storageFailure(
            String operation,
            String objectKey,
            RuntimeException exception
    ) {
        String requestId = extractRequestId(exception);
        log.error(
                "OSS operation failed: operation={}, objectKey={}, requestId={}, exceptionType={}",
                operation,
                objectKey,
                requestId,
                exception.getClass().getSimpleName()
        );
        return new BusinessException(ErrorCode.STORAGE_OPERATION_FAILED);
    }

    /**
     * 关闭 OSS 下载结果，释放 SDK 持有的 HTTP 资源。
     *
     * @param result OSS 下载结果
     */
    private void closeGetObjectResult(GetObjectResult result) {
        try {
            result.close();
        } catch (Exception exception) {
            log.warn("关闭 OSS 下载结果失败：exceptionType={}", exception.getClass().getSimpleName());
        }
    }

    /**
     * 从 SDK 异常链中提取 OSS request ID。
     *
     * @param exception SDK 异常
     * @return request ID，无法取得时返回 {@code unknown}
     */
    private String extractRequestId(RuntimeException exception) {
        if (exception instanceof ServiceException serviceException) {
            return valueOrUnknown(serviceException.requestId());
        }
        if (exception instanceof OperationException operationException) {
            Throwable serviceCause = operationException.contains(ServiceException.class);
            if (serviceCause instanceof ServiceException serviceException) {
                return valueOrUnknown(serviceException.requestId());
            }
        }
        return "unknown";
    }

    /**
     * 校验对象键是后端生成的相对路径。
     *
     * @param objectKey 对象键
     */
    private void validateObjectKey(String objectKey) {
        requireText(objectKey, "objectKey 不能为空");
        if (objectKey.startsWith("/")
                || objectKey.endsWith("/")
                || objectKey.contains("\\")
                || objectKey.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("objectKey 格式非法");
        }
        for (String segment : objectKey.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("objectKey 格式非法");
            }
        }
    }

    /**
     * 校验字符串不为空。
     *
     * @param value 待校验值
     * @param message 异常消息
     */
    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 删除地址末尾的斜杠。
     *
     * @param value 地址
     * @return 规范化地址
     */
    private String stripTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    /**
     * 返回非空诊断字段。
     *
     * @param value 原始字段值
     * @return 原值或 {@code unknown}
     */
    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
