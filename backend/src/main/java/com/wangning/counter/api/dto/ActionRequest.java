package com.wangning.counter.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 点赞、收藏操作请求。
 */
@Data
public class ActionRequest {

    /** 实体类型，当前仅支持 {@code knowpost}。 */
    @NotBlank(message = "实体类型不能为空")
    private String entityType;

    /** 正整数字符串形式的实体 ID。 */
    @NotBlank(message = "实体 ID 不能为空")
    @Pattern(regexp = "[1-9]\\d*", message = "实体 ID 必须为正整数")
    private String entityId;
}
