package com.wangning.knowpost.service;

import com.wangning.knowpost.api.dto.StoragePresignRequest;
import com.wangning.knowpost.api.dto.StoragePresignResponse;

/**
 * 知文浏览器直传 OSS 的预签名服务。
 */
public interface KnowPostUploadService {

    /**
     * 为当前作者的知文生成 OSS PUT 预签名信息。
     *
     * @param userId 当前登录用户 ID
     * @param request 上传请求
     * @return 预签名上传信息
     */
    StoragePresignResponse presignUpload(long userId, StoragePresignRequest request);
}
