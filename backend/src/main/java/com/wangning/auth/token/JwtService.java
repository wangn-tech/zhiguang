package com.wangning.auth.token;

import com.wangning.auth.config.AuthProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * JWT 签发与 Refresh Token 解析服务。
 *
 * <p>令牌仅保存稳定身份信息，不写入昵称、头像等可变用户资料。</p>
 */
@Service
public class JwtService {

    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String CLAIM_USER_ID = "uid";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder refreshJwtDecoder;
    private final AuthProperties authProperties;
    private final Clock clock;

    /**
     * 创建 JWT 服务。
     *
     * @param jwtEncoder JWT 编码器
     * @param refreshJwtDecoder Refresh Token 专用解码器
     * @param authProperties 认证配置
     */
    public JwtService(
            JwtEncoder jwtEncoder,
            @Qualifier("refreshJwtDecoder") JwtDecoder refreshJwtDecoder,
            AuthProperties authProperties
    ) {
        this.jwtEncoder = jwtEncoder;
        this.refreshJwtDecoder = refreshJwtDecoder;
        this.authProperties = authProperties;
        this.clock = Clock.systemUTC();
    }

    /**
     * 为用户签发新的 Access Token 和 Refresh Token。
     *
     * @param userId 用户 ID
     * @return 新令牌对
     */
    public TokenPair issueTokenPair(long userId) {
        Assert.isTrue(userId > 0, "userId must be positive");

        AuthProperties.Jwt properties = authProperties.getJwt();
        Instant issuedAt = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        Instant accessExpiresAt = issuedAt.plus(properties.getAccessTokenTtl());
        Instant refreshExpiresAt = issuedAt.plus(properties.getRefreshTokenTtl());
        String refreshTokenId = UUID.randomUUID().toString();

        String accessToken = encodeToken(
                userId,
                ACCESS_TOKEN_TYPE,
                UUID.randomUUID().toString(),
                issuedAt,
                accessExpiresAt
        );
        String refreshToken = encodeToken(
                userId,
                REFRESH_TOKEN_TYPE,
                refreshTokenId,
                issuedAt,
                refreshExpiresAt
        );
        return new TokenPair(
                accessToken,
                accessExpiresAt,
                refreshToken,
                refreshExpiresAt,
                refreshTokenId
        );
    }

    /**
     * 严格解码 Refresh Token。
     *
     * @param token Refresh Token 字符串
     * @return 已验证的 JWT
     */
    public Jwt decodeRefreshToken(String token) {
        Assert.hasText(token, "token must not be blank");
        return refreshJwtDecoder.decode(token);
    }

    /**
     * 从已验证的 JWT 中提取用户 ID。
     *
     * @param jwt 已验证 JWT
     * @return 用户 ID
     * @throws IllegalArgumentException 用户 ID 缺失或无效时抛出
     */
    public long extractUserId(Jwt jwt) {
        Assert.notNull(jwt, "jwt must not be null");
        Object claim = jwt.getClaim(CLAIM_USER_ID);
        long userId;
        try {
            if (claim instanceof Number number) {
                userId = number.longValue();
            } else if (claim instanceof String text) {
                userId = Long.parseLong(text);
            } else {
                throw new IllegalArgumentException("Invalid user id in token");
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid user id in token", exception);
        }
        if (userId <= 0 || !String.valueOf(userId).equals(jwt.getSubject())) {
            throw new IllegalArgumentException("Invalid user id in token");
        }
        return userId;
    }

    /**
     * 从已验证的 JWT 中提取令牌 ID。
     *
     * @param jwt 已验证 JWT
     * @return jti
     * @throws IllegalArgumentException jti 缺失时抛出
     */
    public String extractTokenId(Jwt jwt) {
        Assert.notNull(jwt, "jwt must not be null");
        if (!StringUtils.hasText(jwt.getId())) {
            throw new IllegalArgumentException("Token id is missing");
        }
        return jwt.getId();
    }

    /**
     * 编码单个 JWT。
     *
     * @param userId 用户 ID
     * @param tokenType 令牌类型
     * @param tokenId jti
     * @param issuedAt 签发时间
     * @param expiresAt 过期时间
     * @return JWT 字符串
     */
    private String encodeToken(
            long userId,
            String tokenType,
            String tokenId,
            Instant issuedAt,
            Instant expiresAt
    ) {
        AuthProperties.Jwt properties = authProperties.getJwt();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(userId))
                .issuedAt(issuedAt)
                .notBefore(issuedAt)
                .expiresAt(expiresAt)
                .id(tokenId)
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .claim(CLAIM_USER_ID, userId)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(properties.getKeyId())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
