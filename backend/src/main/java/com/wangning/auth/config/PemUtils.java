package com.wangning.auth.config;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;

/**
 * RSA PEM 密钥读取工具。
 *
 * <p>支持 PKCS#8 私钥和 X.509 公钥，供 JWT 编码器与解码器使用。</p>
 */
public final class PemUtils {

    private static final String PRIVATE_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PRIVATE_END = "-----END PRIVATE KEY-----";
    private static final String PUBLIC_BEGIN = "-----BEGIN PUBLIC KEY-----";
    private static final String PUBLIC_END = "-----END PUBLIC KEY-----";

    private PemUtils() {
    }

    /**
     * 读取 PKCS#8 格式的 RSA 私钥。
     *
     * @param resource 私钥资源
     * @return RSA 私钥
     * @throws IllegalStateException 资源无法读取或密钥格式无效时抛出
     */
    public static RSAPrivateKey readPrivateKey(Resource resource) {
        try {
            String keyData = extractKeyData(
                    readResource(resource),
                    PRIVATE_BEGIN,
                    PRIVATE_END
            );
            byte[] keyBytes = Base64.getDecoder().decode(keyData);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (IOException | GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to read RSA private key", exception);
        }
    }

    /**
     * 读取 X.509 格式的 RSA 公钥。
     *
     * @param resource 公钥资源
     * @return RSA 公钥
     * @throws IllegalStateException 资源无法读取或密钥格式无效时抛出
     */
    public static RSAPublicKey readPublicKey(Resource resource) {
        try {
            String keyData = extractKeyData(
                    readResource(resource),
                    PUBLIC_BEGIN,
                    PUBLIC_END
            );
            byte[] keyBytes = Base64.getDecoder().decode(keyData);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (IOException | GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to read RSA public key", exception);
        }
    }

    /**
     * 提取 PEM 头尾之间的 Base64 数据。
     *
     * @param pem PEM 文本
     * @param begin 开始标记
     * @param end 结束标记
     * @return 移除空白后的 Base64 数据
     */
    private static String extractKeyData(String pem, String begin, String end) {
        if (!pem.contains(begin) || !pem.contains(end)) {
            throw new IllegalArgumentException("Invalid PEM key format");
        }
        return pem.replace(begin, "")
                .replace(end, "")
                .replaceAll("\\s", "");
    }

    /**
     * 以 UTF-8 读取资源。
     *
     * @param resource 待读取资源
     * @return 资源文本
     * @throws IOException 资源读取失败时抛出
     */
    private static String readResource(Resource resource) throws IOException {
        Objects.requireNonNull(resource, "resource must not be null");
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
