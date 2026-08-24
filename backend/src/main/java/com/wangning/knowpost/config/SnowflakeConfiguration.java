package com.wangning.knowpost.config;

import com.wangning.knowpost.domain.SnowflakeIdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 知文雪花 ID 生成器配置。
 */
@Configuration(proxyBeanMethods = false)
public class SnowflakeConfiguration {

    /**
     * 创建应用内唯一的雪花 ID 生成器。
     *
     * @param properties 已绑定的节点配置
     * @return 线程安全的 ID 生成器
     */
    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator(SnowflakeProperties properties) {
        properties.validate();
        return new SnowflakeIdGenerator(properties.getDatacenterId(), properties.getWorkerId());
    }
}
