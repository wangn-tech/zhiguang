package com.wangning.storage.model;

/**
 * 已存储对象信息。
 *
 * @param objectKey OSS 对象键
 * @param publicUrl 稳定公开访问地址
 * @param eTag OSS 返回的对象 ETag，可能为空
 */
public record StoredObject(
        String objectKey,
        String publicUrl,
        String eTag
) {
}
