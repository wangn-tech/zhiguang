package com.wangning.auth.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 认证模块配置，对应 {@code auth.*} 配置项。
 */
@Data
@Validated
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    /** JWT 签发和密钥配置。 */
    @Valid
    private Jwt jwt = new Jwt();

    /** 验证码配置。 */
    @Valid
    private Verification verification = new Verification();

    /** 密码策略配置。 */
    @Valid
    private Password password = new Password();

    /** 跨域来源配置。 */
    @Valid
    private Cors cors = new Cors();

    /**
     * JWT 配置。
     */
    @Data
    public static class Jwt {

        /** JWT 签发者。 */
        @NotBlank
        private String issuer = "zhiguang";

        /** JWT 密钥标识。 */
        @NotBlank
        private String keyId = "zhiguang-key";

        /** Access Token 有效期。 */
        @NotNull
        private Duration accessTokenTtl = Duration.ofMinutes(15);

        /** Refresh Token 有效期。 */
        @NotNull
        private Duration refreshTokenTtl = Duration.ofDays(7);

        /** RSA 私钥资源。 */
        @NotNull
        private Resource privateKey;

        /** RSA 公钥资源。 */
        @NotNull
        private Resource publicKey;
    }

    /**
     * 验证码配置。
     */
    @Data
    public static class Verification {

        /** 验证码位数。 */
        @Min(4)
        @Max(8)
        private int codeLength = 6;

        /** 验证码有效期。 */
        @NotNull
        private Duration ttl = Duration.ofMinutes(5);

        /** 单个验证码允许的最大错误次数。 */
        @Min(1)
        private int maxAttempts = 5;

        /** 同一账号和场景的发送间隔。 */
        @NotNull
        private Duration sendInterval = Duration.ofSeconds(60);

        /** 同一账号和场景的每日发送上限。 */
        @Min(1)
        private int dailyLimit = 10;
    }

    /**
     * 密码策略配置。
     */
    @Data
    public static class Password {

        /** BCrypt cost。 */
        @Min(4)
        @Max(31)
        private int bcryptStrength = 12;

        /** 密码最小长度。 */
        @Min(1)
        private int minLength = 8;

        /** 密码最大长度。 */
        @Min(1)
        private int maxLength = 64;
    }

    /**
     * CORS 配置。
     */
    @Data
    public static class Cors {

        /** 允许访问后端的前端来源。 */
        @NotEmpty
        private List<@NotBlank String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));
    }
}
