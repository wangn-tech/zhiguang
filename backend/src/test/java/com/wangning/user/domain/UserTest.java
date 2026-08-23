package com.wangning.user.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void shouldExcludePasswordHashFromToString() {
        User user = User.builder()
                .id(1L)
                .nickname("测试用户")
                .passwordHash("sensitive-password-hash")
                .build();

        assertThat(user.toString())
                .contains("测试用户")
                .doesNotContain("sensitive-password-hash");
    }
}
