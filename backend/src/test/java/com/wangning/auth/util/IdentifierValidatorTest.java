package com.wangning.auth.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IdentifierValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"13800138000", "15912345678", "18888888888"})
    void shouldAcceptValidPhone(String phone) {
        assertThat(IdentifierValidator.isValidPhone(phone)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"12800138000", "1380013800", "138001380000", "13800138abc"})
    void shouldRejectInvalidPhone(String phone) {
        assertThat(IdentifierValidator.isValidPhone(phone)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"user@example.com", "USER+tag@EXAMPLE.COM", "first.last@school.edu.cn"})
    void shouldAcceptValidEmail(String email) {
        assertThat(IdentifierValidator.isValidEmail(email)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"user", "user@", "@example.com", "user@example"})
    void shouldRejectInvalidEmail(String email) {
        assertThat(IdentifierValidator.isValidEmail(email)).isFalse();
    }
}
