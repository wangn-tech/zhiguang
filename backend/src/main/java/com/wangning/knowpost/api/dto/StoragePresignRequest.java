package com.wangning.knowpost.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 知文 OSS PUT 预签名请求。
 *
 * @param scene 上传场景：{@code knowpost_content} 或 {@code knowpost_image}
 * @param postId 字符串形式的知文 ID
 * @param contentType 上传对象的 MIME 类型
 * @param ext 前端提交的文件扩展名，可为空
 */
public record StoragePresignRequest(
        @NotBlank(message = "上传场景不能为空") String scene,
        @NotBlank(message = "知文 ID 不能为空") String postId,
        @NotBlank(message = "文件类型不能为空") String contentType,
        String ext
) {
}
