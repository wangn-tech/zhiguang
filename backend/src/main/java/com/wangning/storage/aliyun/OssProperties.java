package com.wangning.storage.aliyun;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

/**
 * 阿里云 OSS 的非敏感运行配置。
 *
 * <p>AccessKey 不属于该配置对象。SDK 通过环境变量凭证提供器直接读取
 * {@code OSS_ACCESS_KEY_ID} 和 {@code OSS_ACCESS_KEY_SECRET}，避免凭证进入
 * Spring 配置对象或调试输出。</p>
 */
@Data
@ConfigurationProperties(prefix = "storage.oss")
public class OssProperties {

    /** 是否启用阿里云 OSS。 */
    private boolean enabled;

    /** Bucket 所在地域，例如 {@code cn-beijing}。 */
    private String region;

    /** 可选的 OSS Endpoint 覆盖地址。 */
    private String endpoint;

    /** OSS Bucket 名称。 */
    private String bucket;

    /** 可选的自定义域名或 CDN 基础地址。 */
    private String publicBaseUrl;

    /** 头像文件大小上限。 */
    private DataSize avatarMaxSize = DataSize.ofMegabytes(5);

    /** PUT 预签名 URL 的默认有效期。 */
    private Duration presignTtl = Duration.ofMinutes(10);

    /**
     * 校验启用 OSS 时必须提供的非敏感配置。
     *
     * @throws IllegalStateException 配置缺失或限制值非法时抛出
     */
    public void validateEnabledConfiguration() {
        requireText(region, "storage.oss.region 不能为空");
        requireText(bucket, "storage.oss.bucket 不能为空");
        if (avatarMaxSize == null || avatarMaxSize.toBytes() <= 0) {
            throw new IllegalStateException("storage.oss.avatar-max-size 必须大于 0");
        }
        if (presignTtl == null || presignTtl.isNegative() || presignTtl.isZero()) {
            throw new IllegalStateException("storage.oss.presign-ttl 必须大于 0");
        }
    }

    /**
     * 校验文本配置不为空。
     *
     * @param value 配置值
     * @param message 校验失败消息
     */
    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
    }
}
