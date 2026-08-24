package com.wangning.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.common.exception.ErrorCode;
import com.wangning.common.web.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 无状态 JWT API 安全配置。
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_AUTH_ENDPOINTS = {
            "/api/v1/auth/send-code",
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/token/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/password/reset"
    };

    /**
     * 配置无状态安全过滤链。
     *
     * @param http Spring Security HTTP 配置
     * @param accessJwtDecoder 只接受 Access Token 的 JWT 解码器
     * @param authenticationEntryPoint 统一 401 处理器
     * @param accessDeniedHandler 统一 403 处理器
     * @return 安全过滤链
     * @throws Exception 配置失败时抛出
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Qualifier("accessJwtDecoder") JwtDecoder accessJwtDecoder,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_ENDPOINTS).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/knowposts/feed",
                                "/api/v1/knowposts/detail/*",
                                "/api/v1/relation/following",
                                "/api/v1/relation/followers",
                                "/api/v1/relation/counter"
                        ).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.decoder(accessJwtDecoder))
                        .authenticationEntryPoint(authenticationEntryPoint));
        return http.build();
    }

    /**
     * 创建统一 JSON 401 处理器。
     *
     * @param objectMapper JSON 序列化器
     * @return 认证失败处理器
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, exception) -> writeErrorResponse(
                request,
                response,
                objectMapper,
                ErrorCode.UNAUTHORIZED
        );
    }

    /**
     * 创建统一 JSON 403 处理器。
     *
     * @param objectMapper JSON 序列化器
     * @return 权限不足处理器
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, exception) -> writeErrorResponse(
                request,
                response,
                objectMapper,
                ErrorCode.FORBIDDEN
        );
    }

    /**
     * 创建基于明确来源白名单的 CORS 配置。
     *
     * @param authProperties 认证配置
     * @return CORS 配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(AuthProperties authProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.copyOf(
                authProperties.getCors().getAllowedOrigins()
        ));
        configuration.setAllowedMethods(List.of(
                "GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"
        ));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * 写入与业务异常一致的安全错误响应。
     *
     * @param request 当前请求
     * @param response 当前响应
     * @param objectMapper JSON 序列化器
     * @param errorCode 错误码
     * @throws IOException 响应写入失败时抛出
     */
    private void writeErrorResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            ObjectMapper objectMapper,
            ErrorCode errorCode
    ) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ErrorResponse.of(
                        errorCode,
                        errorCode.getDefaultMessage(),
                        request.getRequestURI()
                )
        );
    }
}
