package com.wangning.counter.api;

import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.counter.api.dto.CountsResponse;
import com.wangning.counter.service.CounterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 实体点赞、收藏计数查询接口。
 */
@RestController
@RequestMapping("/api/v1/counter")
@RequiredArgsConstructor
public class CounterController {

    private static final List<String> DEFAULT_METRICS = List.of("like", "fav");
    private static final Set<String> SUPPORTED_METRICS = Set.copyOf(DEFAULT_METRICS);

    private final CounterService counterService;

    /**
     * 读取指定实体的互动计数。
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @param metricsStr 可选的逗号分隔指标列表
     * @return 实体类型、实体 ID 与请求指标的计数
     */
    @GetMapping("/{entityType}/{entityId}")
    public CountsResponse getCounts(
            @PathVariable String entityType,
            @PathVariable String entityId,
            @RequestParam(value = "metrics", required = false) String metricsStr
    ) {
        List<String> metrics = parseMetrics(metricsStr);
        return new CountsResponse(entityType, entityId, counterService.getCounts(entityType, entityId, metrics));
    }

    private List<String> parseMetrics(String metricsStr) {
        if (metricsStr == null || metricsStr.isBlank()) {
            return DEFAULT_METRICS;
        }
        List<String> metrics = Arrays.stream(metricsStr.split(","))
                .map(String::trim)
                .toList();
        if (metrics.isEmpty() || metrics.stream().anyMatch(metric -> !SUPPORTED_METRICS.contains(metric))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "计数指标仅支持 like、fav");
        }
        if (metrics.stream().distinct().count() != metrics.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "计数指标不能重复");
        }
        return metrics;
    }
}
