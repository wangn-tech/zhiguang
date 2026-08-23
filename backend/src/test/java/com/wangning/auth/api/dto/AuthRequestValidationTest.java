package com.wangning.auth.api.dto;

import com.wangning.auth.model.IdentifierType;
import com.wangning.auth.verification.VerificationScene;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void shouldRejectIncompleteSendCodeRequest() {
        SendCodeRequest request = new SendCodeRequest(null, null, " ");

        Set<ConstraintViolation<SendCodeRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactlyInAnyOrder(
                        "验证码场景不能为空",
                        "账号类型不能为空",
                        "账号不能为空"
                );
    }

    @Test
    void shouldRequirePasswordAndAcceptedTermsForRegistration() {
        RegisterRequest request = new RegisterRequest(
                IdentifierType.PHONE,
                "13800138000",
                "123456",
                " ",
                false
        );

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactlyInAnyOrder("密码不能为空", "请先同意用户协议");
    }

    @Test
    void shouldAcceptCompleteRegistrationRequest() {
        RegisterRequest request = new RegisterRequest(
                IdentifierType.PHONE,
                "13800138000",
                "123456",
                "password1",
                true
        );

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void shouldPreserveLoginCredentialsForServiceValidation() {
        LoginRequest request = new LoginRequest(
                IdentifierType.EMAIL,
                "user@example.com",
                "123456",
                null
        );

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.code()).isEqualTo("123456");
        assertThat(request.password()).isNull();
    }

    @Test
    void shouldExposeOnlySupportedIdentifierTypes() {
        assertThat(IdentifierType.values()).containsExactly(IdentifierType.PHONE, IdentifierType.EMAIL);
    }
}
