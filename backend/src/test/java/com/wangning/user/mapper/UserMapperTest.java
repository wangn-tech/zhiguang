package com.wangning.user.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.user.domain.User;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MybatisTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserMapperTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("zhiguang_test")
            .withUsername("zhiguang")
            .withPassword("zhiguang_test")
            .withInitScript("schema/user-schema.sql");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    private UserMapper userMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldInsertAndFindUserById() throws Exception {
        User user = completeUser("13800138000", "user@example.com", "zg_1001");

        int affectedRows = userMapper.insert(user);
        User stored = userMapper.findById(user.getId());

        assertThat(affectedRows).isEqualTo(1);
        assertThat(user.getId()).isPositive();
        assertThat(stored)
                .usingRecursiveComparison()
                .ignoringFields("tagsJson")
                .isEqualTo(user);
        assertThat(objectMapper.readTree(stored.getTagsJson()))
                .isEqualTo(objectMapper.readTree(user.getTagsJson()));
    }

    @Test
    void shouldFindAndCheckExistenceByPhoneAndEmail() {
        User user = completeUser("13800138001", "lookup@example.com", null);
        userMapper.insert(user);

        assertThat(userMapper.findByPhone("13800138001").getId()).isEqualTo(user.getId());
        assertThat(userMapper.findByEmail("lookup@example.com").getId()).isEqualTo(user.getId());
        assertThat(userMapper.existsByPhone("13800138001")).isTrue();
        assertThat(userMapper.existsByEmail("lookup@example.com")).isTrue();
    }

    @Test
    void shouldReturnEmptyResultsForUnknownIdentifier() {
        assertThat(userMapper.findById(Long.MAX_VALUE)).isNull();
        assertThat(userMapper.findByPhone("13999999999")).isNull();
        assertThat(userMapper.findByEmail("missing@example.com")).isNull();
        assertThat(userMapper.existsByPhone("13999999999")).isFalse();
        assertThat(userMapper.existsByEmail("missing@example.com")).isFalse();
    }

    @Test
    void shouldRejectDuplicatePhone() {
        userMapper.insert(completeUser("13800138002", null, null));

        assertThatThrownBy(() -> userMapper.insert(completeUser("13800138002", null, null)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void shouldRejectDuplicateEmail() {
        userMapper.insert(completeUser(null, "duplicate@example.com", null));

        assertThatThrownBy(() -> userMapper.insert(completeUser(null, "duplicate@example.com", null)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private User completeUser(String phone, String email, String zgId) {
        Instant timestamp = Instant.parse("2026-08-24T02:00:00Z");
        return User.builder()
                .phone(phone)
                .email(email)
                .passwordHash("password-hash")
                .nickname("测试用户")
                .avatar("https://example.com/avatar.png")
                .bio("个人简介")
                .zgId(zgId)
                .gender("UNKNOWN")
                .birthday(LocalDate.of(2000, 1, 1))
                .school("同济大学")
                .tagsJson("[\"Java\",\"MyBatis\"]")
                .createdAt(timestamp)
                .updatedAt(timestamp)
                .build();
    }
}
