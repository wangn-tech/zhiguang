package com.wangning.knowpost.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 知文置顶状态更新请求。
 *
 * @param isTop 是否置顶
 */
public record KnowPostTopPatchRequest(@NotNull(message = "置顶状态不能为空") Boolean isTop) {
}
