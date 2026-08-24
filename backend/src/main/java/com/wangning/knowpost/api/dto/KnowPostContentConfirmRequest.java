package com.wangning.knowpost.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 正文 OSS 上传确认请求。
 *
 * @param objectKey 正文对象键
 * @param etag 浏览器从 OSS 响应取得的 ETag
 * @param size 浏览器取得的文件字节数
 * @param sha256 浏览器计算的 SHA-256
 */
public record KnowPostContentConfirmRequest(
        @NotBlank(message = "正文对象键不能为空") String objectKey,
        @NotBlank(message = "正文 ETag 不能为空") String etag,
        @NotNull(message = "正文字节数不能为空") Long size,
        @NotBlank(message = "正文 SHA-256 不能为空") String sha256
) {
}
