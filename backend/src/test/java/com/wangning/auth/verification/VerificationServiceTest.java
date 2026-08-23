package com.wangning.auth.verification;

import com.wangning.auth.config.AuthProperties;
import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    private static final VerificationScene SCENE = VerificationScene.REGISTER;
    private static final String IDENTIFIER = "13800138000";

    @Mock
    private VerificationCodeStore codeStore;

    @Mock
    private CodeSender codeSender;

    @Mock
    private VerificationRateLimiter rateLimiter;

    private AuthProperties authProperties;
    private VerificationService verificationService;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        verificationService = new VerificationService(
                codeStore,
                codeSender,
                rateLimiter,
                authProperties
        );
    }

    @Test
    void shouldGenerateStoreAndSendCodeAfterAcquiringPermit() {
        when(rateLimiter.tryAcquire(SCENE, IDENTIFIER))
                .thenReturn(VerificationRateLimitResult.ALLOWED);

        SendCodeResult result = verificationService.sendCode(SCENE, IDENTIFIER);

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(codeStore).saveCode(
                org.mockito.ArgumentMatchers.eq(SCENE),
                org.mockito.ArgumentMatchers.eq(IDENTIFIER),
                codeCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(5)),
                org.mockito.ArgumentMatchers.eq(5)
        );
        verify(codeSender).sendCode(SCENE, IDENTIFIER, codeCaptor.getValue(), 5);
        assertThat(codeCaptor.getValue()).matches("\\d{6}");
        assertThat(result).isEqualTo(new SendCodeResult(IDENTIFIER, SCENE, 300));
    }

    @Test
    void shouldRejectRequestDuringCooldown() {
        when(rateLimiter.tryAcquire(SCENE, IDENTIFIER))
                .thenReturn(VerificationRateLimitResult.TOO_FREQUENT);

        assertThatThrownBy(() -> verificationService.sendCode(SCENE, IDENTIFIER))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.VERIFICATION_RATE_LIMIT)
                );

        verifyNoInteractions(codeStore, codeSender);
    }

    @Test
    void shouldRejectRequestAfterDailyLimit() {
        when(rateLimiter.tryAcquire(SCENE, IDENTIFIER))
                .thenReturn(VerificationRateLimitResult.DAILY_LIMIT_REACHED);

        assertThatThrownBy(() -> verificationService.sendCode(SCENE, IDENTIFIER))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.VERIFICATION_RATE_LIMIT)
                );

        verifyNoInteractions(codeStore, codeSender);
    }

    @Test
    void shouldInvalidateStoredCodeWhenSenderFails() {
        when(rateLimiter.tryAcquire(SCENE, IDENTIFIER))
                .thenReturn(VerificationRateLimitResult.ALLOWED);
        doThrow(new IllegalStateException("send failed"))
                .when(codeSender)
                .sendCode(
                        org.mockito.ArgumentMatchers.eq(SCENE),
                        org.mockito.ArgumentMatchers.eq(IDENTIFIER),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(5)
                );

        assertThatThrownBy(() -> verificationService.sendCode(SCENE, IDENTIFIER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("send failed");

        verify(codeStore).invalidate(SCENE, IDENTIFIER);
    }

    @Test
    void shouldDelegateVerificationToStore() {
        VerificationCheckResult expected = new VerificationCheckResult(
                VerificationCodeStatus.SUCCESS,
                0,
                5
        );
        when(codeStore.verify(SCENE, IDENTIFIER, "123456")).thenReturn(expected);

        VerificationCheckResult actual = verificationService.verify(
                SCENE,
                IDENTIFIER,
                "123456"
        );

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void shouldRejectIncompleteVerificationParameters() {
        assertThatThrownBy(() -> verificationService.verify(SCENE, IDENTIFIER, " "))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST)
                );

        verifyNoInteractions(codeStore);
    }

    @Test
    void shouldDelegateInvalidationToStore() {
        verificationService.invalidate(VerificationScene.LOGIN, IDENTIFIER);

        verify(codeStore).invalidate(VerificationScene.LOGIN, IDENTIFIER);
    }
}

@ExtendWith(OutputCaptureExtension.class)
class LoggingCodeSenderTest {

    @Test
    void shouldLogVerificationCodeForDevelopment(CapturedOutput output) {
        new LoggingCodeSender().sendCode(
                VerificationScene.LOGIN,
                "user@example.com",
                "012345",
                5
        );

        assertThat(output)
                .contains("scene=LOGIN")
                .contains("identifier=user@example.com")
                .contains("code=012345")
                .contains("expireMinutes=5");
    }
}
