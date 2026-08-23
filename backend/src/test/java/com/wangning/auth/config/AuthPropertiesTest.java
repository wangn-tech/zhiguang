package com.wangning.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AuthPropertiesTest {

    @Autowired
    private AuthProperties properties;

    @Test
    void shouldBindAuthenticationConfiguration() {
        assertThat(properties.getJwt().getIssuer()).isEqualTo("zhiguang");
        assertThat(properties.getJwt().getAccessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(properties.getJwt().getRefreshTokenTtl()).isEqualTo(Duration.ofDays(7));
        assertThat(properties.getJwt().getPrivateKey().getFilename()).isEqualTo("private.pem");
        assertThat(properties.getJwt().getPublicKey().getFilename()).isEqualTo("public.pem");

        assertThat(properties.getVerification().getCodeLength()).isEqualTo(6);
        assertThat(properties.getVerification().getTtl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.getVerification().getMaxAttempts()).isEqualTo(5);
        assertThat(properties.getVerification().getSendInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.getVerification().getDailyLimit()).isEqualTo(10);

        assertThat(properties.getPassword().getBcryptStrength()).isEqualTo(12);
        assertThat(properties.getPassword().getMinLength()).isEqualTo(8);
        assertThat(properties.getPassword().getMaxLength()).isEqualTo(64);
        assertThat(properties.getCors().getAllowedOrigins()).containsExactly("http://localhost:5173");
    }
}
