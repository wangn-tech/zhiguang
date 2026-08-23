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
import com.wangning.auth.util.IdentifierValidator;
import com.wangning.auth.verification.SendCodeResult;
import com.wangning.auth.verification.VerificationCheckResult;
import com.wangning.auth.verification.VerificationCodeStatus;
import com.wangning.auth.verification.VerificationScene;
import com.wangning.auth.verification.VerificationService;
import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.user.domain.User;
import com.wangning.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * 认证用例编排服务。
 *
 * <p>负责验证码发送、注册、登录、令牌刷新与退出、密码重置和当前用户查询。
 * 持久化、验证码、令牌和审计细节分别委托给对应的下层服务。</p>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String EMPTY_TAGS_JSON = "[]";

    private final UserService userService;
    private final VerificationService verificationService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenStore refreshTokenStore;
    private final LoginLogService loginLogService;
    private final AuthProperties authProperties;

    /**
     * 校验账号和场景后发送验证码。
     *
     * @param request 验证码发送请求
     * @return 验证码过期信息
     */
    public SendCodeResponse sendCode(SendCodeRequest request) {
        if (request == null || request.scene() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        String identifier = normalizeAndValidateIdentifier(
                request.identifierType(),
                request.identifier()
        );
        boolean exists = identifierExists(request.identifierType(), identifier);

        if (request.scene() == VerificationScene.REGISTER && exists) {
            throw new BusinessException(ErrorCode.IDENTIFIER_EXISTS);
        }
        if (request.scene() != VerificationScene.REGISTER && !exists) {
            throw new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND);
        }

        SendCodeResult result = verificationService.sendCode(request.scene(), identifier);
        return new SendCodeResponse(
                result.identifier(),
                result.scene(),
                result.expireSeconds()
        );
    }

    /**
     * 使用验证码注册用户并建立刷新会话。
     *
     * @param request 注册请求
     * @param clientInfo 客户端信息
     * @return 用户和令牌响应
     */
    public AuthResponse register(RegisterRequest request, ClientInfo clientInfo) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        if (!request.agreeTerms()) {
            throw new BusinessException(ErrorCode.TERMS_NOT_ACCEPTED);
        }

        String identifier = normalizeAndValidateIdentifier(
                request.identifierType(),
                request.identifier()
        );
        validatePassword(request.password());
        if (identifierExists(request.identifierType(), identifier)) {
            throw new BusinessException(ErrorCode.IDENTIFIER_EXISTS);
        }

        try {
            ensureVerificationSuccess(verificationService.verify(
                    VerificationScene.REGISTER,
                    identifier,
                    request.code()
            ));
        } catch (BusinessException exception) {
            recordAudit(null, identifier, LoginChannel.REGISTER, clientInfo, LoginStatus.FAILED);
            throw exception;
        }

        User user = User.builder()
                .phone(request.identifierType() == IdentifierType.PHONE ? identifier : null)
                .email(request.identifierType() == IdentifierType.EMAIL ? identifier : null)
                .passwordHash(passwordEncoder.encode(request.password()))
                .nickname(generateDefaultNickname())
                .avatar(null)
                .bio(null)
                .zgId(null)
                .gender(null)
                .birthday(null)
                .school(null)
                .tagsJson(EMPTY_TAGS_JSON)
                .build();
        User createdUser = userService.createUser(user);
        TokenPair tokenPair = jwtService.issueTokenPair(createdUser.getId());
        storeRefreshToken(createdUser.getId(), tokenPair);
        recordAudit(
                createdUser.getId(),
                identifier,
                LoginChannel.REGISTER,
                clientInfo,
                LoginStatus.SUCCESS
        );
        return new AuthResponse(mapUser(createdUser), mapToken(tokenPair));
    }

    /**
     * 使用密码或验证码登录并建立刷新会话。
     *
     * @param request 登录请求
     * @param clientInfo 客户端信息
     * @return 用户和令牌响应
     */
    public AuthResponse login(LoginRequest request, ClientInfo clientInfo) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        String identifier = normalizeAndValidateIdentifier(
                request.identifierType(),
                request.identifier()
        );
        LoginChannel channel = resolveLoginChannel(request);
        Optional<User> userOptional = findUserByIdentifier(request.identifierType(), identifier);

        if (userOptional.isEmpty()) {
            recordAudit(null, identifier, channel, clientInfo, LoginStatus.FAILED);
            throw new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND);
        }

        User user = userOptional.get();
        if (channel == LoginChannel.PASSWORD) {
            if (!passwordMatches(request.password(), user.getPasswordHash())) {
                recordAudit(user.getId(), identifier, channel, clientInfo, LoginStatus.FAILED);
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            }
        } else {
            try {
                ensureVerificationSuccess(verificationService.verify(
                        VerificationScene.LOGIN,
                        identifier,
                        request.code()
                ));
            } catch (BusinessException exception) {
                recordAudit(user.getId(), identifier, channel, clientInfo, LoginStatus.FAILED);
                throw exception;
            }
        }

        TokenPair tokenPair = jwtService.issueTokenPair(user.getId());
        storeRefreshToken(user.getId(), tokenPair);
        recordAudit(user.getId(), identifier, channel, clientInfo, LoginStatus.SUCCESS);
        return new AuthResponse(mapUser(user), mapToken(tokenPair));
    }

    /**
     * 使用 Refresh Token 原子轮换新的令牌对。
     *
     * @param request 令牌刷新请求
     * @return 新令牌响应
     */
    public TokenResponse refresh(TokenRefreshRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        Jwt currentToken = decodeRefreshToken(request.refreshToken());
        long userId;
        String currentTokenId;
        try {
            userId = jwtService.extractUserId(currentToken);
            currentTokenId = jwtService.extractTokenId(currentToken);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        if (userService.findById(userId).isEmpty()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        TokenPair nextTokenPair = jwtService.issueTokenPair(userId);
        boolean rotated = refreshTokenStore.rotateToken(
                userId,
                currentTokenId,
                nextTokenPair.refreshTokenId(),
                remainingTtl(nextTokenPair.refreshTokenExpiresAt())
        );
        if (!rotated) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        return mapToken(nextTokenPair);
    }

    /**
     * 幂等撤销 Refresh Token 会话。
     *
     * <p>无效、过期或已撤销令牌按退出成功处理；合法令牌对应的 Redis 撤销异常仍向上抛出。</p>
     *
     * @param refreshToken Refresh Token 字符串
     */
    public void logout(String refreshToken) {
        long userId;
        String tokenId;
        try {
            Jwt jwt = jwtService.decodeRefreshToken(refreshToken);
            userId = jwtService.extractUserId(jwt);
            tokenId = jwtService.extractTokenId(jwt);
        } catch (JwtException | IllegalArgumentException exception) {
            return;
        }
        refreshTokenStore.revokeToken(userId, tokenId);
    }

    /**
     * 使用验证码重置密码，并撤销该用户的全部刷新会话。
     *
     * @param request 密码重置请求
     */
    public void resetPassword(PasswordResetRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        String identifier = normalizeAndValidateIdentifier(
                request.identifierType(),
                request.identifier()
        );
        validatePassword(request.newPassword());
        User user = findUserByIdentifier(request.identifierType(), identifier)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
        ensureVerificationSuccess(verificationService.verify(
                VerificationScene.RESET_PASSWORD,
                identifier,
                request.code()
        ));

        String passwordHash = passwordEncoder.encode(request.newPassword());
        userService.updatePasswordHash(user.getId(), passwordHash);
        refreshTokenStore.revokeAll(user.getId());
    }

    /**
     * 查询当前用户最新资料。
     *
     * @param userId 用户 ID
     * @return 当前用户响应
     */
    public AuthUserResponse me(long userId) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
        return mapUser(user);
    }

    /**
     * 标准化并校验手机号或邮箱。
     *
     * @param type 账号类型
     * @param rawIdentifier 原始账号
     * @return 标准化账号
     */
    private String normalizeAndValidateIdentifier(
            IdentifierType type,
            String rawIdentifier
    ) {
        if (type == null || !StringUtils.hasText(rawIdentifier)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "账号不能为空");
        }
        String identifier = rawIdentifier.trim();
        if (type == IdentifierType.EMAIL) {
            identifier = identifier.toLowerCase(Locale.ROOT);
        }

        boolean valid = switch (type) {
            case PHONE -> IdentifierValidator.isValidPhone(identifier);
            case EMAIL -> IdentifierValidator.isValidEmail(identifier);
        };
        if (!valid) {
            String message = type == IdentifierType.PHONE ? "手机号格式错误" : "邮箱格式错误";
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
        return identifier;
    }

    /**
     * 校验密码长度和复杂度，不改变密码原文。
     *
     * @param password 原始密码
     */
    private void validatePassword(String password) {
        AuthProperties.Password properties = authProperties.getPassword();
        if (!StringUtils.hasText(password)
                || password.length() < properties.getMinLength()
                || password.length() > properties.getMaxLength()) {
            throw new BusinessException(
                    ErrorCode.PASSWORD_POLICY_VIOLATION,
                    "密码长度必须为 %d～%d 个字符".formatted(
                            properties.getMinLength(),
                            properties.getMaxLength()
                    )
            );
        }
        boolean hasLetter = password.codePoints().anyMatch(Character::isLetter);
        boolean hasDigit = password.codePoints().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BusinessException(
                    ErrorCode.PASSWORD_POLICY_VIOLATION,
                    "密码必须同时包含字母和数字"
            );
        }
    }

    /**
     * 确定登录渠道，并校验验证码与密码严格二选一。
     *
     * @param request 登录请求
     * @return 登录渠道
     */
    private LoginChannel resolveLoginChannel(LoginRequest request) {
        boolean hasCode = StringUtils.hasText(request.code());
        boolean hasPassword = StringUtils.hasText(request.password());
        if (hasCode == hasPassword) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码和密码必须且只能提供一个");
        }
        return hasPassword ? LoginChannel.PASSWORD : LoginChannel.CODE;
    }

    /**
     * 将验证码内部状态转换为稳定业务错误码。
     *
     * @param result 验证码校验结果
     */
    private void ensureVerificationSuccess(VerificationCheckResult result) {
        if (result == null || result.status() == null) {
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID);
        }
        VerificationCodeStatus status = result.status();
        switch (status) {
            case SUCCESS -> {
                return;
            }
            case TOO_MANY_ATTEMPTS -> throw new BusinessException(
                    ErrorCode.VERIFICATION_TOO_MANY_ATTEMPTS
            );
            case NOT_FOUND, MISMATCH -> throw new BusinessException(
                    ErrorCode.VERIFICATION_CODE_INVALID
            );
        }
    }

    /**
     * 判断账号是否存在。
     *
     * @param type 账号类型
     * @param identifier 标准化账号
     * @return 存在时返回 {@code true}
     */
    private boolean identifierExists(IdentifierType type, String identifier) {
        return switch (type) {
            case PHONE -> userService.existsByPhone(identifier);
            case EMAIL -> userService.existsByEmail(identifier);
        };
    }

    /**
     * 根据账号查询用户。
     *
     * @param type 账号类型
     * @param identifier 标准化账号
     * @return 用户 Optional
     */
    private Optional<User> findUserByIdentifier(IdentifierType type, String identifier) {
        return switch (type) {
            case PHONE -> userService.findByPhone(identifier);
            case EMAIL -> userService.findByEmail(identifier);
        };
    }

    /**
     * 判断密码是否匹配，数据库中的异常哈希按凭证失败处理。
     *
     * @param rawPassword 原始密码
     * @param passwordHash BCrypt 密码哈希
     * @return 匹配时返回 {@code true}
     */
    private boolean passwordMatches(String rawPassword, String passwordHash) {
        if (!StringUtils.hasText(passwordHash)) {
            return false;
        }
        try {
            return passwordEncoder.matches(rawPassword, passwordHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * 保存新 Refresh Token 会话。
     *
     * @param userId 用户 ID
     * @param tokenPair 新令牌对
     */
    private void storeRefreshToken(long userId, TokenPair tokenPair) {
        refreshTokenStore.storeToken(
                userId,
                tokenPair.refreshTokenId(),
                remainingTtl(tokenPair.refreshTokenExpiresAt())
        );
    }

    /**
     * 计算令牌剩余有效期。
     *
     * @param expiresAt 令牌过期时间
     * @return 正数 TTL
     */
    private Duration remainingTtl(Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalStateException("Refresh token has already expired");
        }
        return ttl;
    }

    /**
     * 严格解码 Refresh Token，并统一转换业务错误。
     *
     * @param refreshToken Refresh Token 字符串
     * @return 已验证 JWT
     */
    private Jwt decodeRefreshToken(String refreshToken) {
        try {
            return jwtService.decodeRefreshToken(refreshToken);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
    }

    /**
     * 记录认证审计事件。
     *
     * @param userId 用户 ID
     * @param identifier 标准化账号
     * @param channel 认证渠道
     * @param clientInfo 客户端信息
     * @param status 认证结果
     */
    private void recordAudit(
            Long userId,
            String identifier,
            LoginChannel channel,
            ClientInfo clientInfo,
            LoginStatus status
    ) {
        String ip = clientInfo == null ? null : clientInfo.ip();
        String userAgent = clientInfo == null ? null : clientInfo.userAgent();
        loginLogService.record(userId, identifier, channel, ip, userAgent, status);
    }

    /**
     * 映射用户响应，避免暴露密码哈希。
     *
     * @param user 用户实体
     * @return 认证用户响应
     */
    private AuthUserResponse mapUser(User user) {
        String tagsJson = StringUtils.hasText(user.getTagsJson())
                ? user.getTagsJson()
                : EMPTY_TAGS_JSON;
        return new AuthUserResponse(
                user.getId(),
                user.getNickname(),
                user.getAvatar(),
                user.getPhone(),
                user.getEmail(),
                user.getZgId(),
                user.getBirthday(),
                user.getSchool(),
                user.getBio(),
                user.getGender(),
                tagsJson
        );
    }

    /**
     * 映射令牌响应，不对外暴露 jti。
     *
     * @param tokenPair 令牌对
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
     * 生成不包含账号信息的默认昵称。
     *
     * @return 随机默认昵称
     */
    private String generateDefaultNickname() {
        return "知光用户" + UUID.randomUUID().toString().substring(0, 8);
    }
}
