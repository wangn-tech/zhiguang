package com.wangning.knowpost.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 知文雪花 ID 的节点配置。
 *
 * <p>两个节点号由部署环境显式注入。所有同时写入同一数据库的实例必须使用不同的节点号组合，
 * 否则可能生成重复 ID。</p>
 */
@Data
@ConfigurationProperties(prefix = "knowpost.snowflake")
public class SnowflakeProperties {

    /** 数据中心节点号，取值范围为 0-31。 */
    private Long datacenterId;

    /** 工作节点号，取值范围为 0-31。 */
    private Long workerId;

    /**
     * 校验启动所需的节点配置。
     *
     * @throws IllegalStateException 节点号缺失或超出允许范围时抛出
     */
    public void validate() {
        validateNodeId(datacenterId, "knowpost.snowflake.datacenter-id");
        validateNodeId(workerId, "knowpost.snowflake.worker-id");
    }

    /**
     * 校验单个节点号。
     *
     * @param nodeId 节点号
     * @param propertyName 配置项名称
     * @throws IllegalStateException 节点号缺失或不在 0-31 范围内时抛出
     */
    private void validateNodeId(Long nodeId, String propertyName) {
        if (nodeId == null || nodeId < 0 || nodeId > 31) {
            throw new IllegalStateException(propertyName + " 必须在 0-31 范围内");
        }
    }
}
