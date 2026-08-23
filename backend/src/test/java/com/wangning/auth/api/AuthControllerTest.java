package com.wangning.auth.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.auth.api.dto.AuthResponse;
import com.wangning.auth.api.dto.AuthUserResponse;
import com.wangning.auth.api.dto.LoginRequest;
import com.wangning.auth.api.dto.RegisterRequest;
import com.wangning.auth.api.dto.SendCodeRequest;
import com.wangning.auth.api.dto.SendCodeResponse;
import com.wangning.auth.api.dto.TokenRefreshRequest;
import com.wangning.auth.api.dto.TokenResponse;
import com.wangning.auth.config.AuthProperties;
import com.wangning.auth.config.SecurityConfig;
import com.wangning.auth.model.ClientInfo;
import com.wangning.auth.model.IdentifierType;
import com.wangning.auth.service.AuthService;
import com.wangning.auth.token.JwtService;
import com.wangning.auth.verification.VerificationScene;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, AuthControllerTest.TestConfig.class})
class AuthControllerTest {

    private static final String BASE_PATH = "/api/v1/auth";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccessDeniedHandler accessDeniedHandler;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @MockBean(name = "accessJwtDecoder")
    private JwtDecoder accessJwtDecoder;

    @Test
    void shouldAllowSendingCodeWithoutAccessToken() throws Exception {
        SendCodeResponse response = new SendCodeResponse(
                "13800138000",
                VerificationScene.REGISTER,
                300
        );
        when(authService.sendCode(any(SendCodeRequest.class))).thenReturn(response);

        mockMvc.perform(post(BASE_PATH + "/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scene": "REGISTER",
                                  "identifierType": "PHONE",
                                  "identifier": "13800138000"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identifier").value("13800138000"))
                .andExpect(jsonPath("$.scene").value("REGISTER"))
                .andExpect(jsonPath("$.expireSeconds").value(300));
    }

    @Test
    void shouldRegisterWithoutAccessTokenAndUseRemoteAddress() throws Exception {
        when(authService.register(any(RegisterRequest.class), any(ClientInfo.class)))
                .thenReturn(authResponse());

        mockMvc.perform(post(BASE_PATH + "/register")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.10");
                            return request;
                        })
                        .header("X-Forwarded-For", "198.51.100.20")
                        .header(HttpHeaders.USER_AGENT, "JUnit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifierType": "EMAIL",
                                  "identifier": "user@example.com",
                                  "code": "123456",
                                  "password": "Password1",
                                  "agreeTerms": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(42))
                .andExpect(jsonPath("$.user.email").value("user@example.com"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.token.accessToken").value("access-token"));

        ArgumentCaptor<ClientInfo> clientInfoCaptor = ArgumentCaptor.forClass(ClientInfo.class);
        verify(authService).register(any(RegisterRequest.class), clientInfoCaptor.capture());
        assertThat(clientInfoCaptor.getValue())
                .isEqualTo(new ClientInfo("203.0.113.10", "JUnit"));
    }

    @Test
    void shouldAllowLoginAndRefreshWithoutAccessToken() throws Exception {
        when(authService.login(any(LoginRequest.class), any(ClientInfo.class)))
                .thenReturn(authResponse());
        when(authService.refresh(any(TokenRefreshRequest.class)))
                .thenReturn(tokenResponse());

        mockMvc.perform(post(BASE_PATH + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifierType": "PHONE",
                                  "identifier": "13800138000",
                                  "code": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(42));

        mockMvc.perform(post(BASE_PATH + "/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    @Test
    void shouldAllowLogoutAndPasswordResetWithoutAccessToken() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(post(BASE_PATH + "/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifierType": "PHONE",
                                  "identifier": "13800138000",
                                  "code": "123456",
                                  "newPassword": "NewPassword1"
                                }
                                """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void shouldRequireAccessTokenForCurrentUser() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("请先登录"))
                .andExpect(jsonPath("$.path").value(BASE_PATH + "/me"));
    }

    @Test
    void shouldReturnJsonUnauthorizedForInvalidBearerToken() throws Exception {
        when(accessJwtDecoder.decode("broken-token"))
                .thenThrow(new BadJwtException("invalid token"));

        mockMvc.perform(get(BASE_PATH + "/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer broken-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturnCurrentUserForAuthenticatedAccessToken() throws Exception {
        when(jwtService.extractUserId(any(Jwt.class))).thenReturn(42L);
        when(authService.me(42L)).thenReturn(authUserResponse());

        mockMvc.perform(get(BASE_PATH + "/me")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("42")
                                .claim("uid", 42L)
                                .claim("token_type", "access"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.zgId").value("zg_42"));
        verify(authService).me(42L);
    }

    @Test
    void shouldReturnBadRequestForValidationAndUnknownEnum() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scene": "REGISTER",
                                  "identifierType": "PHONE",
                                  "identifier": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("账号不能为空"));

        mockMvc.perform(post(BASE_PATH + "/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scene": "UNKNOWN",
                                  "identifierType": "PHONE",
                                  "identifier": "13800138000"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("请求参数错误"));
    }

    @Test
    void shouldAllowConfiguredCorsPreflightAndRejectUnknownOrigin() throws Exception {
        mockMvc.perform(options(BASE_PATH + "/me")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5173"
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true"
                ));

        mockMvc.perform(options(BASE_PATH + "/me")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void shouldWriteUnifiedForbiddenResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(
                request,
                response,
                new AccessDeniedException("forbidden")
        );

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(body.path("code").asText()).isEqualTo("FORBIDDEN");
        assertThat(body.path("message").asText()).isEqualTo("无权执行该操作");
        assertThat(body.path("path").asText()).isEqualTo("/api/v1/admin");
    }

    /**
     * 创建认证组合响应。
     *
     * @return 认证响应
     */
    private AuthResponse authResponse() {
        return new AuthResponse(authUserResponse(), tokenResponse());
    }

    /**
     * 创建用户响应。
     *
     * @return 用户响应
     */
    private AuthUserResponse authUserResponse() {
        return new AuthUserResponse(
                42L,
                "测试用户",
                null,
                null,
                "user@example.com",
                "zg_42",
                null,
                null,
                null,
                null,
                "[]"
        );
    }

    /**
     * 创建令牌响应。
     *
     * @return 令牌响应
     */
    private TokenResponse tokenResponse() {
        return new TokenResponse(
                "access-token",
                Instant.parse("2026-08-24T05:00:00Z"),
                "refresh-token",
                Instant.parse("2026-08-31T05:00:00Z")
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {

        /**
         * 提供 Web 安全测试使用的默认认证配置。
         *
         * @return 认证配置
         */
        @Bean
        AuthProperties authProperties() {
            return new AuthProperties();
        }
    }
}
