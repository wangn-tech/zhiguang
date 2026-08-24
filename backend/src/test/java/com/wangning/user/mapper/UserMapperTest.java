package com.wangning.user.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.auth.audit.LoginLog;
import com.wangning.auth.audit.LoginLogMapper;
import com.wangning.user.domain.User;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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

    @Autowired
    private LoginLogMapper loginLogMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void shouldListUsersByIds() {
        User first = completeUser("13800138016", "first@example.com", "zg_first");
        User second = completeUser("13800138017", "second@example.com", "zg_second_list");
        userMapper.insert(first);
        userMapper.insert(second);

        List<User> users = userMapper.listByIds(List.of(second.getId(), first.getId()));

        assertThat(users)
                .extracting(User::getId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
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

    @Test
    void shouldUpdatePasswordHash() {
        User user = completeUser("13800138009", null, null);
        userMapper.insert(user);

        int affectedRows = userMapper.updatePasswordHash(user.getId(), "new-password-hash");

        assertThat(affectedRows).isEqualTo(1);
        assertThat(userMapper.findById(user.getId()).getPasswordHash())
                .isEqualTo("new-password-hash");
        assertThat(userMapper.updatePasswordHash(Long.MAX_VALUE, "unused-hash")).isZero();
    }

    @Test
    void shouldCheckZgIdExcludingCurrentUser() {
        User first = completeUser("13800138010", null, "zg_unique");
        User second = completeUser("13800138011", null, "zg_second");
        userMapper.insert(first);
        userMapper.insert(second);

        assertThat(userMapper.existsByZgIdExceptId("zg_unique", second.getId())).isTrue();
        assertThat(userMapper.existsByZgIdExceptId("zg_unique", first.getId())).isFalse();
        assertThat(userMapper.existsByZgIdExceptId("ZG_UNIQUE", second.getId())).isTrue();
        assertThat(userMapper.existsByZgIdExceptId("zg_missing", second.getId())).isFalse();
    }

    @Test
    void shouldUpdateProfileAndPreserveCredentialsAndAvatar() throws Exception {
        User user = completeUser("13800138012", "profile@example.com", "zg_before");
        userMapper.insert(user);
        User profile = User.builder()
                .id(user.getId())
                .nickname("更新后的昵称")
                .bio(null)
                .zgId("zg_after")
                .gender(null)
                .birthday(null)
                .school("新的学校")
                .tagsJson("[]")
                .build();

        int affectedRows = userMapper.updateProfile(profile);
        User stored = userMapper.findById(user.getId());

        assertThat(affectedRows).isEqualTo(1);
        assertThat(stored.getNickname()).isEqualTo("更新后的昵称");
        assertThat(stored.getBio()).isNull();
        assertThat(stored.getZgId()).isEqualTo("zg_after");
        assertThat(stored.getGender()).isNull();
        assertThat(stored.getBirthday()).isNull();
        assertThat(stored.getSchool()).isEqualTo("新的学校");
        assertThat(objectMapper.readTree(stored.getTagsJson()))
                .isEqualTo(objectMapper.readTree("[]"));
        assertThat(stored.getPhone()).isEqualTo("13800138012");
        assertThat(stored.getEmail()).isEqualTo("profile@example.com");
        assertThat(stored.getPasswordHash()).isEqualTo("password-hash");
        assertThat(stored.getAvatar()).isEqualTo("https://example.com/avatar.png");
    }

    @Test
    void shouldReturnZeroWhenUpdatingUnknownProfile() {
        User profile = User.builder()
                .id(Long.MAX_VALUE)
                .nickname("不存在的用户")
                .tagsJson("[]")
                .build();

        assertThat(userMapper.updateProfile(profile)).isZero();
    }

    @Test
    void shouldUpdateAvatarAndPreserveOtherUserFields() {
        User user = completeUser("13800138015", "avatar@example.com", "zg_avatar");
        userMapper.insert(user);
        String newAvatar = "https://static.example.com/avatars/%d/new.png".formatted(user.getId());

        int affectedRows = userMapper.updateAvatar(user.getId(), newAvatar);
        User stored = userMapper.findById(user.getId());

        assertThat(affectedRows).isEqualTo(1);
        assertThat(stored.getAvatar()).isEqualTo(newAvatar);
        assertThat(stored.getPhone()).isEqualTo(user.getPhone());
        assertThat(stored.getEmail()).isEqualTo(user.getEmail());
        assertThat(stored.getPasswordHash()).isEqualTo(user.getPasswordHash());
        assertThat(stored.getNickname()).isEqualTo(user.getNickname());
        assertThat(userMapper.updateAvatar(Long.MAX_VALUE, newAvatar)).isZero();
    }

    @Test
    void shouldRejectDuplicateZgIdDuringProfileUpdate() {
        User first = completeUser("13800138013", null, "zg_taken");
        User second = completeUser("13800138014", null, "zg_available");
        userMapper.insert(first);
        userMapper.insert(second);
        User profile = User.builder()
                .id(second.getId())
                .nickname(second.getNickname())
                .bio(second.getBio())
                .zgId("zg_taken")
                .gender(second.getGender())
                .birthday(second.getBirthday())
                .school(second.getSchool())
                .tagsJson(second.getTagsJson())
                .build();

        assertThatThrownBy(() -> userMapper.updateProfile(profile))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void shouldInsertLoginAuditAndReturnGeneratedId() {
        Instant createdAt = Instant.parse("2026-08-24T02:30:00Z");
        LoginLog loginLog = LoginLog.builder()
                .identifier("missing@example.com")
                .channel("CODE")
                .ip("127.0.0.1")
                .userAgent("JUnit")
                .status("FAILED")
                .createdAt(createdAt)
                .build();

        int affectedRows = loginLogMapper.insert(loginLog);

        assertThat(affectedRows).isEqualTo(1);
        assertThat(loginLog.getId()).isPositive();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT identifier FROM login_logs WHERE id = ?",
                String.class,
                loginLog.getId()
        )).isEqualTo("missing@example.com");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM login_logs WHERE id = ?",
                String.class,
                loginLog.getId()
        )).isEqualTo("FAILED");
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
