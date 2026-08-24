package com.wangning.search.api.dto;

import java.util.List;

/**
 * 搜索标题联想结果。
 *
 * @param items 去重后的候选标题列表
 */
public record SuggestResponse(List<String> items) {
}
