package com.wangning.counter.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启用计数聚合器需要的定时任务支持。
 */
@Configuration
@EnableScheduling
public class CounterSchedulingConfiguration {
}
