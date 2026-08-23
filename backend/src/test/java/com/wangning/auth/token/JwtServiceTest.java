package com.wangning.auth.token;

import com.wangning.auth.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class JwtServiceTest {

    private static final long USER_ID = 42L;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    @Qualifier("accessJwtDecoder")
    private JwtDecoder accessJwtDecoder;

    @Autowired
    @Qualifier("refreshJwtDecoder")
    private JwtDecoder refreshJwtDecoder;

    @Autowired
    private AuthProperties authProperties;

    @Test
    void shouldIssueStrictAccessAndRefreshTokens() {
        TokenPair pair = jwtService.issueTokenPair(USER_ID);

        Jwt accessToken = accessJwtDecoder.decode(pair.accessToken());
        Jwt refreshToken = jwtService.decodeRefreshToken(pair.refreshToken());

        assertTokenClaims(accessToken, "access", USER_ID);
        assertTokenClaims(refreshToken, "refresh", USER_ID);
        assertThat(accessToken.getId()).isNotEqualTo(refreshToken.getId());
        assertThat(refreshToken.getId()).isEqualTo(pair.refreshTokenId());
        assertThat(Duration.between(accessToken.getIssuedAt(), pair.accessTokenExpiresAt()))
                .isEqualTo(authProperties.getJwt().getAccessTokenTtl());
        assertThat(Duration.between(refreshToken.getIssuedAt(), pair.refreshTokenExpiresAt()))
                .isEqualTo(authProperties.getJwt().getRefreshTokenTtl());
    }

    @Test
    void shouldRejectRefreshTokenAsAccessTokenAndViceVersa() {
        TokenPair pair = jwtService.issueTokenPair(USER_ID);

        assertThatThrownBy(() -> accessJwtDecoder.decode(pair.refreshToken()))
                .isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> refreshJwtDecoder.decode(pair.accessToken()))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void shouldRejectTokenWithWrongIssuer() {
        String token = encodeToken(
                "another-issuer",
                "access",
                String.valueOf(USER_ID),
                USER_ID,
                "wrong-issuer-token",
                Instant.now().minusSeconds(1),
                Instant.now().plusSeconds(300)
        );

        assertThatThrownBy(() -> accessJwtDecoder.decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void shouldRejectExpiredToken() {
        Instant issuedAt = Instant.now().minusSeconds(300);
        String token = encodeToken(
                authProperties.getJwt().getIssuer(),
                "refresh",
                String.valueOf(USER_ID),
                USER_ID,
                "expired-token",
                issuedAt,
                issuedAt.plusSeconds(30)
        );

        assertThatThrownBy(() -> refreshJwtDecoder.decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void shouldRejectMismatchedSubjectAndUserId() {
        String token = encodeToken(
                authProperties.getJwt().getIssuer(),
                "refresh",
                "43",
                USER_ID,
                "mismatched-user-token",
                Instant.now().minusSeconds(1),
                Instant.now().plusSeconds(300)
        );

        assertThatThrownBy(() -> refreshJwtDecoder.decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void shouldRejectTokenWithModifiedSignature() {
        String token = jwtService.issueTokenPair(USER_ID).accessToken();
        int signatureStart = token.lastIndexOf('.') + 1;
        char firstSignatureCharacter = token.charAt(signatureStart);
        char replacement = firstSignatureCharacter == 'A' ? 'B' : 'A';
        String modified = token.substring(0, signatureStart)
                + replacement
                + token.substring(signatureStart + 1);

        assertThatThrownBy(() -> accessJwtDecoder.decode(modified))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shouldRejectTokenWithoutRequiredIdentifierOrTimeClaims() {
        Instant issuedAt = Instant.now().minusSeconds(1);
        Instant expiresAt = issuedAt.plusSeconds(300);
        JwtClaimsSet missingTokenId = JwtClaimsSet.builder()
                .issuer(authProperties.getJwt().getIssuer())
                .subject(String.valueOf(USER_ID))
                .issuedAt(issuedAt)
                .notBefore(issuedAt)
                .expiresAt(expiresAt)
                .claim("token_type", "access")
                .claim("uid", USER_ID)
                .build();
        JwtClaimsSet missingNotBefore = JwtClaimsSet.builder()
                .issuer(authProperties.getJwt().getIssuer())
                .subject(String.valueOf(USER_ID))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id("missing-not-before")
                .claim("token_type", "access")
                .claim("uid", USER_ID)
                .build();

        assertThatThrownBy(() -> accessJwtDecoder.decode(encodeClaims(missingTokenId)))
                .isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> accessJwtDecoder.decode(encodeClaims(missingNotBefore)))
                .isInstanceOf(JwtValidationException.class);
    }

    /**
     * 使用应用私钥编码测试 JWT。
     *
     * @param issuer 签发者
     * @param tokenType 令牌类型
     * @param subject subject
     * @param userId uid
     * @param tokenId jti
     * @param issuedAt 签发时间和生效时间
     * @param expiresAt 过期时间
     * @return JWT 字符串
     */
    private String encodeToken(
            String issuer,
            String tokenType,
            String subject,
            long userId,
            String tokenId,
            Instant issuedAt,
            Instant expiresAt
    ) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(subject)
                .issuedAt(issuedAt)
                .notBefore(issuedAt)
                .expiresAt(expiresAt)
                .id(tokenId)
                .claim("token_type", tokenType)
                .claim("uid", userId)
                .build();
        return encodeClaims(claims);
    }

    /**
     * 编码已经组装好的测试声明。
     *
     * @param claims JWT 声明
     * @return JWT 字符串
     */
    private String encodeClaims(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(authProperties.getJwt().getKeyId())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * 断言令牌包含约定的身份、类型和时间声明。
     *
     * @param jwt 已解码 JWT
     * @param tokenType 预期令牌类型
     * @param userId 预期用户 ID
     */
    private void assertTokenClaims(Jwt jwt, String tokenType, long userId) {
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(authProperties.getJwt().getIssuer());
        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(userId));
        assertThat(jwt.getClaimAsString("token_type")).isEqualTo(tokenType);
        assertThat(((Number) jwt.getClaim("uid")).longValue()).isEqualTo(userId);
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getNotBefore()).isEqualTo(jwt.getIssuedAt());
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
        assertThat(jwt.getHeaders())
                .containsEntry("alg", "RS256")
                .containsEntry("kid", authProperties.getJwt().getKeyId());
    }
}
