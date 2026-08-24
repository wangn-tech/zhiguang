package com.wangning.knowpost.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 知文可见性更新请求。
 *
 * @param visible 可见性值
 */
public record KnowPostVisibilityPatchRequest(@NotBlank(message = "可见性不能为空") String visible) {
}
