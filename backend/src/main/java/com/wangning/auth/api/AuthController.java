package com.wangning.auth.api;

import com.wangning.auth.api.dto.AuthResponse;
import com.wangning.auth.api.dto.AuthUserResponse;
import com.wangning.auth.api.dto.LoginRequest;
import com.wangning.auth.api.dto.LogoutRequest;
import com.wangning.auth.api.dto.PasswordResetRequest;
import com.wangning.auth.api.dto.RegisterRequest;
import com.wangning.auth.api.dto.SendCodeRequest;
import com.wangning.auth.api.dto.SendCodeResponse;
import com.wangning.auth.api.dto.TokenRefreshRequest;
import com.wangning.auth.api.dto.TokenResponse;
import com.wangning.auth.model.ClientInfo;
import com.wangning.auth.service.AuthService;
import com.wangning.auth.token.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证 REST API。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    /**
     * 发送注册、登录或重置密码验证码。
     *
     * @param request 验证码发送请求
     * @return 验证码过期信息
     */
    @PostMapping("/send-code")
    public SendCodeResponse sendCode(@Valid @RequestBody SendCodeRequest request) {
        return authService.sendCode(request);
    }

    /**
     * 注册用户并自动登录。
     *
     * @param request 注册请求
     * @param httpRequest HTTP 请求
     * @return 用户和令牌响应
     */
    @PostMapping("/register")
    public AuthResponse register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest
    ) {
        return authService.register(request, resolveClientInfo(httpRequest));
    }

    /**
     * 使用验证码或密码登录。
     *
     * @param request 登录请求
     * @param httpRequest HTTP 请求
     * @return 用户和令牌响应
     */
    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        return authService.login(request, resolveClientInfo(httpRequest));
    }

    /**
     * 原子轮换 Refresh Token。
     *
     * @param request 刷新请求
     * @return 新令牌响应
     */
    @PostMapping("/token/refresh")
    public TokenResponse refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return authService.refresh(request);
    }

    /**
     * 撤销当前 Refresh Token 会话。
     *
     * @param request 退出请求
     * @return 204 空响应
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * 使用验证码重置密码。
     *
     * @param request 密码重置请求
     * @return 204 空响应
     */
    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody PasswordResetRequest request
    ) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * 查询当前用户最新资料。
     *
     * @param jwt 已通过 Access Token 解码器校验的 JWT
     * @return 当前用户响应
     */
    @GetMapping("/me")
    public AuthUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.me(jwtService.extractUserId(jwt));
    }

    /**
     * 提取审计需要的客户端信息。
     *
     * <p>当前只信任 Servlet 容器提供的远端地址，不直接读取客户端可伪造的转发头。</p>
     *
     * @param request HTTP 请求
     * @return 客户端信息
     */
    private ClientInfo resolveClientInfo(HttpServletRequest request) {
        return new ClientInfo(
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );
    }
}
