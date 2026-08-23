package com.wangning.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * JWT 编码和解码配置。
 *
 * <p>Access Token 与 Refresh Token 使用相同 RSA 密钥，但分别使用只接受对应
 * {@code token_type} 的解码器，避免两类令牌混用。</p>
 *
 * <p><strong>生产风险：</strong>当前按照已确认的原项目方案从 classpath 读取开发密钥，
 * 私钥会随 JAR 一起打包。正式生产环境必须更换密钥，并重新评估由外部密钥管理服务或
 * 受控挂载提供私钥的方式。</p>
 */
@Configuration
@RequiredArgsConstructor
public class AuthConfiguration {

    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String CLAIM_USER_ID = "uid";

    private final AuthProperties authProperties;

    /**
     * 创建使用 RS256 的 JWT 编码器。
     *
     * @return JWT 编码器
     */
    @Bean
    public JwtEncoder jwtEncoder() {
        AuthProperties.Jwt properties = authProperties.getJwt();
        RSAPrivateKey privateKey = PemUtils.readPrivateKey(properties.getPrivateKey());
        RSAPublicKey publicKey = PemUtils.readPublicKey(properties.getPublicKey());
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(properties.getKeyId())
                .algorithm(com.nimbusds.jose.JWSAlgorithm.RS256)
                .build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * 创建只接受 Access Token 的资源服务器解码器。
     *
     * @return Access Token 解码器
     */
    @Bean
    @Primary
    public JwtDecoder accessJwtDecoder() {
        return createDecoder("access");
    }

    /**
     * 创建只接受 Refresh Token 的解码器。
     *
     * @return Refresh Token 解码器
     */
    @Bean
    public JwtDecoder refreshJwtDecoder() {
        return createDecoder("refresh");
    }

    /**
     * 创建包含时间、签发者、令牌类型和身份声明校验的解码器。
     *
     * @param expectedTokenType 允许的令牌类型
     * @return 严格校验的 JWT 解码器
     */
    private JwtDecoder createDecoder(String expectedTokenType) {
        AuthProperties.Jwt properties = authProperties.getJwt();
        RSAPublicKey publicKey = PemUtils.readPublicKey(properties.getPublicKey());
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.getIssuer()),
                tokenTypeValidator(expectedTokenType),
                AuthConfiguration::validateIdentityClaims
        );
        decoder.setJwtValidator(validator);
        return decoder;
    }

    /**
     * 创建令牌类型校验器。
     *
     * @param expectedTokenType 允许的令牌类型
     * @return JWT 校验器
     */
    private OAuth2TokenValidator<Jwt> tokenTypeValidator(String expectedTokenType) {
        return jwt -> {
            if (expectedTokenType.equals(jwt.getClaimAsString(CLAIM_TOKEN_TYPE))) {
                return OAuth2TokenValidatorResult.success();
            }
            return validationFailure("JWT token type is invalid");
        };
    }

    /**
     * 校验用户 ID、subject 和令牌 ID。
     *
     * @param jwt 待校验 JWT
     * @return 校验结果
     */
    private static OAuth2TokenValidatorResult validateIdentityClaims(Jwt jwt) {
        if (jwt.getIssuedAt() == null
                || jwt.getNotBefore() == null
                || jwt.getExpiresAt() == null
                || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())) {
            return validationFailure("JWT time claims are invalid");
        }
        Long userId = parsePositiveLong(jwt.getClaim(CLAIM_USER_ID));
        Long subjectUserId = parsePositiveLong(jwt.getSubject());
        if (userId == null || !userId.equals(subjectUserId)) {
            return validationFailure("JWT user identity is invalid");
        }
        if (!StringUtils.hasText(jwt.getId())) {
            return validationFailure("JWT identifier is missing");
        }
        return OAuth2TokenValidatorResult.success();
    }

    /**
     * 将数字或数字字符串解析为正整数用户 ID。
     *
     * @param value 声明值
     * @return 正整数，格式无效时返回 {@code null}
     */
    private static Long parsePositiveLong(Object value) {
        try {
            long parsed;
            if (value instanceof Number number) {
                parsed = new BigDecimal(number.toString()).longValueExact();
            } else if (value instanceof String text) {
                parsed = Long.parseLong(text);
            } else {
                return null;
            }
            return parsed > 0 ? parsed : null;
        } catch (ArithmeticException | NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 创建统一的 JWT 校验失败结果。
     *
     * @param description 失败原因
     * @return 校验失败结果
     */
    private static OAuth2TokenValidatorResult validationFailure(String description) {
        OAuth2Error error = new OAuth2Error("invalid_token", description, null);
        return OAuth2TokenValidatorResult.failure(error);
    }
}
