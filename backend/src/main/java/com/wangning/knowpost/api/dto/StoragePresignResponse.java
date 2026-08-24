package com.wangning.knowpost.api.dto;

import java.util.Map;

/**
 * 知文 OSS PUT 预签名响应。
 *
 * @param objectKey 后端生成的对象键
 * @param putUrl 短期 PUT 签名地址
 * @param headers 上传时必须携带的请求头
 * @param expiresIn 有效秒数
 */
public record StoragePresignResponse(
        String objectKey,
        String putUrl,
        Map<String, String> headers,
        int expiresIn
) {
}
