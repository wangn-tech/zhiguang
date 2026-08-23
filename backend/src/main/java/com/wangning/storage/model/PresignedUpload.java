package com.wangning.storage.model;

import java.time.Duration;
import java.util.Map;

/**
 * PUT 预签名上传信息。
 *
 * @param objectKey OSS 对象键
 * @param putUrl 短期 PUT 上传地址
 * @param headers 上传时必须携带的签名请求头
 * @param expiresIn 有效期
 */
public record PresignedUpload(
        String objectKey,
        String putUrl,
        Map<String, String> headers,
        Duration expiresIn
) {

    /**
     * 防止调用方修改返回的签名请求头。
     */
    public PresignedUpload {
        headers = Map.copyOf(headers);
    }
}
