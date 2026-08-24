package com.wangning.counter.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 计数恢复使用的 Redisson 客户端配置。
 *
 * <p>连接信息复用 Spring Data Redis 的配置，避免为同一个 Redis 实例维护两套地址、密码和数据库号。</p>
 */
@Configuration(proxyBeanMethods = false)
public class RedissonConfiguration {

    /**
     * 创建用于分布式恢复锁、限流器和退避键的 Redisson 客户端。
     *
     * @param redisProperties Spring Boot Redis 连接配置
     * @param recoveryProperties 计数恢复配置
     * @return Redisson 客户端
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            RedisProperties redisProperties,
            CounterRecoveryProperties recoveryProperties
    ) {
        Config config = new Config();
        config.setLockWatchdogTimeout(recoveryProperties.getLockWatchdog().toMillis());

        String scheme = redisProperties.getSsl().isEnabled() ? "rediss://" : "redis://";
        SingleServerConfig server = config.useSingleServer()
                .setAddress(scheme + redisProperties.getHost() + ':' + redisProperties.getPort())
                .setDatabase(redisProperties.getDatabase());
        if (StringUtils.hasText(redisProperties.getUsername())) {
            server.setUsername(redisProperties.getUsername());
        }
        if (StringUtils.hasText(redisProperties.getPassword())) {
            server.setPassword(redisProperties.getPassword());
        }
        return Redisson.create(config);
    }
}
