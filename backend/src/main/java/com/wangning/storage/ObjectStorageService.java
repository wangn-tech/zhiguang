package com.wangning.storage;

import com.wangning.storage.model.PresignedUpload;
import com.wangning.storage.model.StoredObject;

import java.io.InputStream;
import java.time.Duration;

/**
 * 与具体云厂商无关的对象存储接口。
 *
 * <p>业务模块只依赖该接口，不直接使用阿里云 SDK 类型。对象键必须由后端生成，
 * 不能直接信任客户端提供的完整路径。</p>
 */
public interface ObjectStorageService {

    /**
     * 上传一个对象。
     *
     * @param objectKey 后端生成的对象键
     * @param contentType 对象 MIME 类型
     * @param contentLength 对象字节数
     * @param inputStream 对象内容输入流，调用方负责关闭
     * @return 已存储对象信息
     */
    StoredObject upload(
            String objectKey,
            String contentType,
            long contentLength,
            InputStream inputStream
    );

    /**
     * 删除一个对象。删除不存在的对象按成功处理。
     *
     * @param objectKey 后端生成的对象键
     */
    void delete(String objectKey);

    /**
     * 打开一个对象的内容流。
     *
     * <p>调用方必须在读取完成后关闭返回流。该方法仅接受后端生成并已校验的对象键，供搜索索引等
     * 服务端任务读取对象内容，不能用客户端提供的 URL 替代。</p>
     *
     * @param objectKey 后端生成的对象键
     * @return 对象内容流
     */
    InputStream download(String objectKey);

    /**
     * 生成用于 PUT 上传的短期预签名 URL。
     *
     * @param objectKey 后端生成的对象键
     * @param contentType 上传时必须使用的 MIME 类型
     * @param ttl 预签名 URL 有效期
     * @return 预签名上传信息
     */
    PresignedUpload presignUpload(String objectKey, String contentType, Duration ttl);

    /**
     * 生成对象的稳定公开访问地址。
     *
     * @param objectKey 对象键
     * @return 公开访问地址
     */
    String publicUrl(String objectKey);
}
