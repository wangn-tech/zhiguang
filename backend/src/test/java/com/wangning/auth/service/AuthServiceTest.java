package com.wangning.auth.service;

import com.wangning.auth.api.dto.AuthResponse;
import com.wangning.auth.api.dto.AuthUserResponse;
import com.wangning.auth.api.dto.LoginRequest;
import com.wangning.auth.api.dto.PasswordResetRequest;
import com.wangning.auth.api.dto.RegisterRequest;
import com.wangning.auth.api.dto.SendCodeRequest;
import com.wangning.auth.api.dto.SendCodeResponse;
import com.wangning.auth.api.dto.TokenRefreshRequest;
import com.wangning.auth.api.dto.TokenResponse;
import com.wangning.auth.audit.LoginChannel;
import com.wangning.auth.audit.LoginLogService;
import com.wangning.auth.audit.LoginStatus;
import com.wangning.auth.config.AuthProperties;
import com.wangning.auth.model.ClientInfo;
import com.wangning.auth.model.IdentifierType;
import com.wangning.auth.token.JwtService;
import com.wangning.auth.token.RefreshTokenStore;
import com.wangning.auth.token.TokenPair;
import com.wangning.auth.verification.SendCodeResult;
import com.wangning.auth.verification.VerificationCheckResult;
import com.wangning.auth.verification.VerificationCodeStatus;
import com.wangning.auth.verification.VerificationScene;
import com.wangning.auth.verification.VerificationService;
import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.user.domain.User;
import com.wangning.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final ClientInfo CLIENT_INFO = new ClientInfo("127.0.0.1", "JUnit");

    @Mock
    private UserService userService;

    @Mock
    private VerificationService verificationService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private LoginLogService loginLogService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userService,
                verificationService,
                passwordEncoder,
                jwtService,
                refreshTokenStore,
                loginLogService,
                new AuthProperties()
        );
    }

    @Test
    void shouldNormalizeEmailBeforeSendingRegistrationCode() {
        when(verificationService.sendCode(VerificationScene.REGISTER, "user@example.com"))
                .thenReturn(new SendCodeResult(
                        "user@example.com",
                        VerificationScene.REGISTER,
                        300
                ));

        SendCodeResponse response = authService.sendCode(new SendCodeRequest(
                VerificationScene.REGISTER,
                IdentifierType.EMAIL,
                " USER@EXAMPLE.COM "
        ));

        assertThat(response).isEqualTo(new SendCodeResponse(
                "user@example.com",
                VerificationScene.REGISTER,
                300
        ));
        verify(userService).existsByEmail("user@example.com");
    }

    @Test
    void shouldEnforceIdentifierExistenceForCodeScenes() {
        when(userService.existsByPhone("13800138000")).thenReturn(true);

        assertError(
                () -> authService.sendCode(new SendCodeRequest(
                        VerificationScene.REGISTER,
                        IdentifierType.PHONE,
                        "13800138000"
                )),
                ErrorCode.IDENTIFIER_EXISTS
        );
        assertError(
                () -> authService.sendCode(new SendCodeRequest(
                        VerificationScene.LOGIN,
                        IdentifierType.EMAIL,
                        "missing@example.com"
                )),
                ErrorCode.IDENTIFIER_NOT_FOUND
        );
        verifyNoInteractions(verificationService);
    }

    @Test
    void shouldRejectInvalidIdentifierAfterNormalization() {
        assertError(
                () -> authService.sendCode(new SendCodeRequest(
                        VerificationScene.LOGIN,
                        IdentifierType.PHONE,
                        " 12800138000 "
                )),
                ErrorCode.BAD_REQUEST
        );
        verifyNoInteractions(userService, verificationService);
    }

    @Test
    void shouldRegisterWithExactPasswordAndExpectedDefaults() {
        RegisterRequest request = new RegisterRequest(
                IdentifierType.EMAIL,
                " USER@EXAMPLE.COM ",
                "123456",
                " Password1 ",
                true
        );
        TokenPair tokenPair = tokenPair();
        when(verificationService.verify(
                VerificationScene.REGISTER,
                "user@example.com",
                "123456"
        )).thenReturn(successfulVerification());
        when(passwordEncoder.encode(" Password1 ")).thenReturn("password-hash");
        when(userService.createUser(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return user;
        });
        when(jwtService.issueTokenPair(10L)).thenReturn(tokenPair);

        AuthResponse response = authService.register(request, CLIENT_INFO);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userService).createUser(userCaptor.capture());
        User createdUser = userCaptor.getValue();
        assertThat(createdUser.getPhone()).isNull();
        assertThat(createdUser.getEmail()).isEqualTo("user@example.com");
        assertThat(createdUser.getPasswordHash()).isEqualTo("password-hash");
        assertThat(createdUser.getNickname()).matches("知光用户[0-9a-f]{8}");
        assertThat(createdUser.getAvatar()).isNull();
        assertThat(createdUser.getBio()).isNull();
        assertThat(createdUser.getZgId()).isNull();
        assertThat(createdUser.getTagsJson()).isEqualTo("[]");
        verify(passwordEncoder).encode(" Password1 ");
        verify(refreshTokenStore).storeToken(eq(10L), eq("refresh-id"), any(Duration.class));
        verify(loginLogService).record(
                10L,
                "user@example.com",
                LoginChannel.REGISTER,
                "127.0.0.1",
                "JUnit",
                LoginStatus.SUCCESS
        );
        assertThat(response.user().email()).isEqualTo("user@example.com");
        assertThat(response.user().phone()).isNull();
        assertThat(response.token()).isEqualTo(mapToken(tokenPair));
    }

    @Test
    void shouldRejectTermsAndInvalidPasswordBeforeConsumingCode() {
        assertError(
                () -> authService.register(new RegisterRequest(
                        IdentifierType.PHONE,
                        "13800138000",
                        "123456",
                        "Password1",
                        false
                ), CLIENT_INFO),
                ErrorCode.TERMS_NOT_ACCEPTED
        );
        assertError(
                () -> authService.register(new RegisterRequest(
                        IdentifierType.PHONE,
                        "13800138000",
                        "123456",
                        "onlyletters",
                        true
                ), CLIENT_INFO),
                ErrorCode.PASSWORD_POLICY_VIOLATION
        );
        assertError(
                () -> authService.register(new RegisterRequest(
                        IdentifierType.PHONE,
                        "13800138000",
                        "123456",
                        "Short1",
                        true
                ), CLIENT_INFO),
                ErrorCode.PASSWORD_POLICY_VIOLATION
        );
        assertError(
                () -> authService.register(new RegisterRequest(
                        IdentifierType.PHONE,
                        "13800138000",
                        "123456",
                        "A1" + "x".repeat(63),
                        true
                ), CLIENT_INFO),
                ErrorCode.PASSWORD_POLICY_VIOLATION
        );
        verifyNoInteractions(verificationService);
    }

    @Test
    void shouldMapRegistrationVerificationFailureAndAuditIt() {
        when(verificationService.verify(
                VerificationScene.REGISTER,
                "13800138000",
                "000000"
        )).thenReturn(new VerificationCheckResult(
                VerificationCodeStatus.MISMATCH,
                1,
                5
        ));

        assertError(
                () -> authService.register(new RegisterRequest(
                        IdentifierType.PHONE,
                        "13800138000",
                        "000000",
                        "Password1",
                        true
                ), CLIENT_INFO),
                ErrorCode.VERIFICATION_CODE_INVALID
        );
        verify(loginLogService).record(
                null,
                "13800138000",
                LoginChannel.REGISTER,
                "127.0.0.1",
                "JUnit",
                LoginStatus.FAILED
        );
        verify(userService, never()).createUser(any());
    }

    @Test
    void shouldRequireExactlyOneLoginCredential() {
        assertError(
                () -> authService.login(new LoginRequest(
                        IdentifierType.PHONE,
                        "13800138000",
                        null,
                        null
                ), CLIENT_INFO),
                ErrorCode.BAD_REQUEST
        );
        assertError(
                () -> authService.login(new LoginRequest(
                        IdentifierType.PHONE,
                        "13800138000",
                        "123456",
                        "Password1"
                ), CLIENT_INFO),
                ErrorCode.BAD_REQUEST
        );
        verify(userService, never()).findByPhone(any());
    }

    @Test
    void shouldLoginWithExactPassword() {
        User user = user(11L, "13800138000", null);
        TokenPair tokenPair = tokenPair();
        when(userService.findByPhone("13800138000")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(" Password1 ", "stored-hash")).thenReturn(true);
        when(jwtService.issueTokenPair(11L)).thenReturn(tokenPair);

        AuthResponse response = authService.login(new LoginRequest(
                IdentifierType.PHONE,
                " 13800138000 ",
                null,
                " Password1 "
        ), CLIENT_INFO);

        verify(passwordEncoder).matches(" Password1 ", "stored-hash");
        verify(refreshTokenStore).storeToken(eq(11L), eq("refresh-id"), any(Duration.class));
        verify(loginLogService).record(
                11L,
                "13800138000",
                LoginChannel.PASSWORD,
                "127.0.0.1",
                "JUnit",
                LoginStatus.SUCCESS
        );
        assertThat(response.user().id()).isEqualTo(11L);
    }

    @Test
    void shouldAuditPasswordLoginFailure() {
        User user = user(12L, "13800138000", null);
        when(userService.findByPhone("13800138000")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword1", "stored-hash")).thenReturn(false);

        assertError(
                () -> authService.login(new LoginRequest(
                        IdentifierType.PHONE,
                        "13800138000",
                        null,
                        "WrongPassword1"
                ), CLIENT_INFO),
                ErrorCode.INVALID_CREDENTIALS
        );
        verify(loginLogService).record(
                12L,
                "13800138000",
                LoginChannel.PASSWORD,
                "127.0.0.1",
                "JUnit",
                LoginStatus.FAILED
        );
        verifyNoInteractions(jwtService, refreshTokenStore);
    }

    @Test
    void shouldAuditUnknownAccountLogin() {
        when(userService.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertError(
                () -> authService.login(new LoginRequest(
                        IdentifierType.EMAIL,
                        "MISSING@EXAMPLE.COM",
                        "123456",
                        null
                ), CLIENT_INFO),
                ErrorCode.IDENTIFIER_NOT_FOUND
        );
        verify(loginLogService).record(
                null,
                "missing@example.com",
                LoginChannel.CODE,
                "127.0.0.1",
                "JUnit",
                LoginStatus.FAILED
        );
    }

    @Test
    void shouldLoginWithCode() {
        User user = user(13L, null, "user@example.com");
        TokenPair tokenPair = tokenPair();
        when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(verificationService.verify(
                VerificationScene.LOGIN,
                "user@example.com",
                "123456"
        )).thenReturn(successfulVerification());
        when(jwtService.issueTokenPair(13L)).thenReturn(tokenPair);

        AuthResponse response = authService.login(new LoginRequest(
                IdentifierType.EMAIL,
                "USER@EXAMPLE.COM",
                "123456",
                null
        ), null);

        verify(loginLogService).record(
                13L,
                "user@example.com",
                LoginChannel.CODE,
                null,
                null,
                LoginStatus.SUCCESS
        );
        assertThat(response.user().email()).isEqualTo("user@example.com");
    }

    @Test
    void shouldMapVerificationAttemptLimitDuringLogin() {
        User user = user(14L, "13800138000", null);
        when(userService.findByPhone("13800138000")).thenReturn(Optional.of(user));
        when(verificationService.verify(
                VerificationScene.LOGIN,
                "13800138000",
                "000000"
        )).thenReturn(new VerificationCheckResult(
                VerificationCodeStatus.TOO_MANY_ATTEMPTS,
                5,
                5
        ));

        assertError(
                () -> authService.login(new LoginRequest(
                        IdentifierType.PHONE,
                        "13800138000",
                        "000000",
                        null
                ), CLIENT_INFO),
                ErrorCode.VERIFICATION_TOO_MANY_ATTEMPTS
        );
        verify(loginLogService).record(
                14L,
                "13800138000",
                LoginChannel.CODE,
                "127.0.0.1",
                "JUnit",
                LoginStatus.FAILED
        );
    }

    @Test
    void shouldAtomicallyRefreshTokenWithoutSeparateWhitelistCheck() {
        Jwt jwt = org.mockito.Mockito.mock(Jwt.class);
        TokenPair nextTokenPair = tokenPair();
        when(jwtService.decodeRefreshToken("refresh-token")).thenReturn(jwt);
        when(jwtService.extractUserId(jwt)).thenReturn(15L);
        when(jwtService.extractTokenId(jwt)).thenReturn("current-id");
        when(userService.findById(15L)).thenReturn(Optional.of(user(15L, "13800138000", null)));
        when(jwtService.issueTokenPair(15L)).thenReturn(nextTokenPair);
        when(refreshTokenStore.rotateToken(
                eq(15L),
                eq("current-id"),
                eq("refresh-id"),
                any(Duration.class)
        )).thenReturn(true);

        TokenResponse response = authService.refresh(new TokenRefreshRequest("refresh-token"));

        assertThat(response).isEqualTo(mapToken(nextTokenPair));
        verify(refreshTokenStore, never()).isTokenValid(anyLong(), any());
        verify(refreshTokenStore, never()).revokeToken(anyLong(), any());
    }

    @Test
    void shouldRejectInvalidOrAlreadyRotatedRefreshToken() {
        when(jwtService.decodeRefreshToken("invalid"))
                .thenThrow(new JwtException("invalid token"));
        assertError(
                () -> authService.refresh(new TokenRefreshRequest("invalid")),
                ErrorCode.REFRESH_TOKEN_INVALID
        );

        Jwt jwt = org.mockito.Mockito.mock(Jwt.class);
        TokenPair nextTokenPair = tokenPair();
        when(jwtService.decodeRefreshToken("used-token")).thenReturn(jwt);
        when(jwtService.extractUserId(jwt)).thenReturn(16L);
        when(jwtService.extractTokenId(jwt)).thenReturn("used-id");
        when(userService.findById(16L)).thenReturn(Optional.of(user(16L, "13800138000", null)));
        when(jwtService.issueTokenPair(16L)).thenReturn(nextTokenPair);
        when(refreshTokenStore.rotateToken(
                eq(16L),
                eq("used-id"),
                eq("refresh-id"),
                any(Duration.class)
        )).thenReturn(false);

        assertError(
                () -> authService.refresh(new TokenRefreshRequest("used-token")),
                ErrorCode.REFRESH_TOKEN_INVALID
        );
    }

    @Test
    void shouldRevokeValidTokenAndIgnoreInvalidLogoutToken() {
        Jwt jwt = org.mockito.Mockito.mock(Jwt.class);
        when(jwtService.decodeRefreshToken("valid-token")).thenReturn(jwt);
        when(jwtService.extractUserId(jwt)).thenReturn(17L);
        when(jwtService.extractTokenId(jwt)).thenReturn("valid-id");

        authService.logout("valid-token");

        verify(refreshTokenStore).revokeToken(17L, "valid-id");

        when(jwtService.decodeRefreshToken("invalid-token"))
                .thenThrow(new JwtException("invalid token"));
        authService.logout("invalid-token");
        verify(refreshTokenStore, times(1)).revokeToken(anyLong(), any());
    }

    @Test
    void shouldResetExactPasswordAndRevokeAllSessions() {
        User user = user(18L, null, "user@example.com");
        when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(verificationService.verify(
                VerificationScene.RESET_PASSWORD,
                "user@example.com",
                "123456"
        )).thenReturn(successfulVerification());
        when(passwordEncoder.encode(" NewPassword1 ")).thenReturn("new-password-hash");

        authService.resetPassword(new PasswordResetRequest(
                IdentifierType.EMAIL,
                " USER@EXAMPLE.COM ",
                "123456",
                " NewPassword1 "
        ));

        verify(passwordEncoder).encode(" NewPassword1 ");
        verify(userService).updatePasswordHash(18L, "new-password-hash");
        verify(refreshTokenStore).revokeAll(18L);
    }

    @Test
    void shouldNotUpdatePasswordWhenResetCodeIsInvalid() {
        User user = user(19L, "13800138000", null);
        when(userService.findByPhone("13800138000")).thenReturn(Optional.of(user));
        when(verificationService.verify(
                VerificationScene.RESET_PASSWORD,
                "13800138000",
                "000000"
        )).thenReturn(new VerificationCheckResult(
                VerificationCodeStatus.NOT_FOUND,
                0,
                0
        ));

        assertError(
                () -> authService.resetPassword(new PasswordResetRequest(
                        IdentifierType.PHONE,
                        "13800138000",
                        "000000",
                        "NewPassword1"
                )),
                ErrorCode.VERIFICATION_CODE_INVALID
        );
        verify(userService, never()).updatePasswordHash(anyLong(), any());
        verify(refreshTokenStore, never()).revokeAll(anyLong());
    }

    @Test
    void shouldReturnLatestUserWithoutPasswordHash() {
        User user = user(20L, "13800138000", "user@example.com");
        user.setTagsJson(null);
        user.setZgId("zg_20");
        user.setBirthday(LocalDate.of(2000, 1, 1));
        user.setSchool("同济大学");
        user.setBio("简介");
        user.setGender("UNKNOWN");
        when(userService.findById(20L)).thenReturn(Optional.of(user));

        AuthUserResponse response = authService.me(20L);

        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.zgId()).isEqualTo("zg_20");
        assertThat(response.tagJson()).isEqualTo("[]");
        assertThat(response.toString()).doesNotContain("stored-hash");
    }

    /**
     * 创建验证码成功结果。
     *
     * @return 验证码成功结果
     */
    private VerificationCheckResult successfulVerification() {
        return new VerificationCheckResult(VerificationCodeStatus.SUCCESS, 0, 5);
    }

    /**
     * 创建测试用户。
     *
     * @param id 用户 ID
     * @param phone 手机号
     * @param email 邮箱
     * @return 用户
     */
    private User user(long id, String phone, String email) {
        return User.builder()
                .id(id)
                .phone(phone)
                .email(email)
                .passwordHash("stored-hash")
                .nickname("测试用户")
                .tagsJson("[]")
                .build();
    }

    /**
     * 创建尚未过期的测试令牌对。
     *
     * @return 测试令牌对
     */
    private TokenPair tokenPair() {
        Instant now = Instant.now();
        return new TokenPair(
                "access-token",
                now.plusSeconds(900),
                "refresh-token",
                now.plus(Duration.ofDays(7)),
                "refresh-id"
        );
    }

    /**
     * 映射测试令牌响应。
     *
     * @param tokenPair 测试令牌对
     * @return 令牌响应
     */
    private TokenResponse mapToken(TokenPair tokenPair) {
        return new TokenResponse(
                tokenPair.accessToken(),
                tokenPair.accessTokenExpiresAt(),
                tokenPair.refreshToken(),
                tokenPair.refreshTokenExpiresAt()
        );
    }

    /**
     * 断言业务错误码。
     *
     * @param action 待执行操作
     * @param errorCode 预期错误码
     */
    private void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode)
                );
    }
}
