package com.wangning.storage.aliyun;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.OSSClientBuilder;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.EnvironmentVariableCredentialsProvider;
import com.wangning.storage.ObjectStorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS SDK 客户端配置。
 *
 * <p>客户端在 Spring 容器中复用，并在容器关闭时统一释放。只有显式设置
 * {@code storage.oss.enabled=true} 才创建相关 Bean，默认测试和 CI 不需要真实 OSS 凭证。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OssProperties.class)
@ConditionalOnProperty(prefix = "storage.oss", name = "enabled", havingValue = "true")
public class OssConfiguration {

    /**
     * 创建读取官方 OSS 环境变量的凭证提供器。
     *
     * @return OSS 环境变量凭证提供器
     */
    @Bean
    public CredentialsProvider ossCredentialsProvider() {
        return new EnvironmentVariableCredentialsProvider();
    }

    /**
     * 创建应用级 OSS 客户端。
     *
     * @param properties OSS 非敏感配置
     * @param credentialsProvider 环境变量凭证提供器
     * @return 可复用的 OSS 客户端
     */
    @Bean(destroyMethod = "close")
    public OSSClient ossClient(
            OssProperties properties,
            CredentialsProvider credentialsProvider
    ) {
        properties.validateEnabledConfiguration();

        OSSClientBuilder builder = OSSClient.newBuilder()
                .credentialsProvider(credentialsProvider)
                .region(properties.getRegion());
        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            builder.endpoint(properties.getEndpoint());
        }
        return builder.build();
    }

    /**
     * 创建阿里云 OSS 的通用存储适配器。
     *
     * @param ossClient 应用级 OSS 客户端
     * @param properties OSS 非敏感配置
     * @return 通用对象存储服务
     */
    @Bean
    public ObjectStorageService objectStorageService(
            OSSClient ossClient,
            OssProperties properties
    ) {
        return new AliyunOssStorageService(ossClient, properties);
    }
}
