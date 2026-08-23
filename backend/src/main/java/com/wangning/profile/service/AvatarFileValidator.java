package com.wangning.profile.service;

import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.storage.aliyun.OssProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * 用户头像文件校验器。
 *
 * <p>同时校验客户端声明的 MIME 类型和文件头，避免仅根据文件名或
 * {@code Content-Type} 接受伪装文件。当前只允许 JPEG、PNG 和 WebP。</p>
 */
@Component
@RequiredArgsConstructor
public class AvatarFileValidator {

    private static final int SIGNATURE_LENGTH = 12;

    private final OssProperties ossProperties;

    /**
     * 校验头像文件并返回服务端确认的文件类型。
     *
     * @param file 客户端上传的头像文件
     * @return 服务端确认的 MIME 类型和扩展名
     * @throws BusinessException 文件为空、超限、类型不受支持或文件头不匹配时抛出
     */
    public ValidatedAvatar validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "头像文件不能为空");
        }

        long maxSize = ossProperties.getAvatarMaxSize().toBytes();
        if (file.getSize() > maxSize) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "头像文件大小超过限制");
        }

        AvatarType declaredType = AvatarType.fromContentType(file.getContentType());
        AvatarType detectedType = detectType(readSignature(file));
        if (declaredType == null || detectedType == null || declaredType != detectedType) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "头像仅支持 JPEG、PNG 或 WebP 格式"
            );
        }
        return new ValidatedAvatar(detectedType.contentType, detectedType.extension);
    }

    /**
     * 读取识别图片类型所需的文件头。
     *
     * @param file 待校验文件
     * @return 最多 12 字节的文件头
     */
    private byte[] readSignature(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return inputStream.readNBytes(SIGNATURE_LENGTH);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "头像文件读取失败");
        }
    }

    /**
     * 根据常见图片魔数识别文件类型。
     *
     * @param signature 文件头
     * @return 识别出的头像类型，无法识别时返回 {@code null}
     */
    private AvatarType detectType(byte[] signature) {
        if (startsWith(signature, new int[]{0xFF, 0xD8, 0xFF})) {
            return AvatarType.JPEG;
        }
        if (startsWith(signature, new int[]{
                0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        })) {
            return AvatarType.PNG;
        }
        if (signature.length >= 12
                && Arrays.equals(Arrays.copyOfRange(signature, 0, 4), new byte[]{'R', 'I', 'F', 'F'})
                && Arrays.equals(Arrays.copyOfRange(signature, 8, 12), new byte[]{'W', 'E', 'B', 'P'})) {
            return AvatarType.WEBP;
        }
        return null;
    }

    /**
     * 判断文件头是否以指定无符号字节序列开头。
     *
     * @param content 文件头
     * @param prefix 预期字节序列，取值范围为 0-255
     * @return 匹配时返回 {@code true}
     */
    private boolean startsWith(byte[] content, int[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (Byte.toUnsignedInt(content[index]) != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 已完成校验的头像文件信息。
     *
     * @param contentType 服务端确认的 MIME 类型
     * @param extension 服务端确认的不含点号扩展名
     */
    public record ValidatedAvatar(String contentType, String extension) {
    }

    /**
     * 支持的头像图片类型。
     */
    private enum AvatarType {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp");

        private final String contentType;
        private final String extension;

        AvatarType(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        /**
         * 根据客户端声明的 MIME 类型查找支持类型。
         *
         * @param contentType 客户端声明的 MIME 类型
         * @return 支持类型，不受支持时返回 {@code null}
         */
        private static AvatarType fromContentType(String contentType) {
            if (contentType == null) {
                return null;
            }
            String normalized = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            return Arrays.stream(values())
                    .filter(type -> type.contentType.equals(normalized))
                    .findFirst()
                    .orElse(null);
        }
    }
}
