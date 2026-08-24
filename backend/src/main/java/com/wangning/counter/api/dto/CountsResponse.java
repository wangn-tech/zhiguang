package com.wangning.counter.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * 实体互动计数响应。
 */
@Data
@AllArgsConstructor
public class CountsResponse {

    /** 实体类型。 */
    private String entityType;

    /** 实体 ID。 */
    private String entityId;

    /** 指标名到计数值的映射。 */
    private Map<String, Long> counts;
}
