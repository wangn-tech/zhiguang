package com.wangning.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.user.domain.User;
import com.wangning.user.mapper.UserMapper;
import com.wangning.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Optional;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userMapper, new ObjectMapper());
    }

    @Test
    void shouldNormalizeIdentifiersWhenFindingUser() {
        User phoneUser = User.builder().id(1L).phone("13800138000").build();
        User emailUser = User.builder().id(2L).email("user@example.com").build();
        when(userMapper.findByPhone("13800138000")).thenReturn(phoneUser);
        when(userMapper.findByEmail("user@example.com")).thenReturn(emailUser);

        Optional<User> foundByPhone = userService.findByPhone(" 13800138000 ");
        Optional<User> foundByEmail = userService.findByEmail(" USER@EXAMPLE.COM ");

        assertThat(foundByPhone).contains(phoneUser);
        assertThat(foundByEmail).contains(emailUser);
    }

    @Test
    void shouldReturnEmptyForBlankIdentifierWithoutQueryingMapper() {
        assertThat(userService.findByPhone(" ")).isEmpty();
        assertThat(userService.findByEmail(null)).isEmpty();
        assertThat(userService.existsByPhone(null)).isFalse();
        assertThat(userService.existsByEmail(" ")).isFalse();

        verify(userMapper, never()).findByPhone(any());
        verify(userMapper, never()).findByEmail(any());
        verify(userMapper, never()).existsByPhone(any());
        verify(userMapper, never()).existsByEmail(any());
    }

    @Test
    void shouldFilterInvalidAndDuplicateIdsWhenListingUsers() {
        User first = User.builder().id(1L).build();
        User second = User.builder().id(2L).build();
        when(userMapper.listByIds(List.of(2L, 1L))).thenReturn(List.of(second, first));

        List<User> users = userService.listByIds(Arrays.asList(null, -1L, 2L, 1L, 2L, 0L));

        assertThat(users).containsExactly(second, first);
        verify(userMapper).listByIds(List.of(2L, 1L));
    }

    @Test
    void shouldReturnEmptyForEmptyOrInvalidIdListsWithoutQueryingMapper() {
        assertThat(userService.listByIds(null)).isEmpty();
        assertThat(userService.listByIds(List.of())).isEmpty();
        assertThat(userService.listByIds(Arrays.asList(null, 0L, -1L))).isEmpty();

        verify(userMapper, never()).listByIds(any());
    }

    @Test
    void shouldCreateUserWithNormalizedValuesAndDefaults() {
        User user = User.builder()
                .id(99L)
                .phone(" 13800138000 ")
                .email(" USER@EXAMPLE.COM ")
                .nickname(" 测试用户 ")
                .build();
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User inserted = invocation.getArgument(0);
            inserted.setId(1L);
            return 1;
        });

        User created = userService.createUser(user);

        assertThat(created.getId()).isEqualTo(1L);
        assertThat(created.getPhone()).isEqualTo("13800138000");
        assertThat(created.getEmail()).isEqualTo("user@example.com");
        assertThat(created.getNickname()).isEqualTo("测试用户");
        assertThat(created.getTagsJson()).isEqualTo("[]");
        assertThat(created.getCreatedAt()).isNotNull().isEqualTo(created.getUpdatedAt());
        verify(userMapper).existsByPhone("13800138000");
        verify(userMapper).existsByEmail("user@example.com");
    }

    @Test
    void shouldRejectUserWithoutIdentifier() {
        User user = User.builder().nickname("测试用户").build();

        assertBusinessException(
                () -> userService.createUser(user),
                ErrorCode.BAD_REQUEST,
                "手机号和邮箱不能同时为空"
        );
        verify(userMapper, never()).insert(any());
    }

    @Test
    void shouldRejectUserWithoutNickname() {
        User user = User.builder().phone("13800138000").nickname(" ").build();

        assertBusinessException(
                () -> userService.createUser(user),
                ErrorCode.BAD_REQUEST,
                "昵称不能为空"
        );
        verify(userMapper, never()).insert(any());
    }

    @Test
    void shouldRejectExistingPhone() {
        User user = User.builder().phone("13800138000").nickname("测试用户").build();
        when(userMapper.existsByPhone("13800138000")).thenReturn(true);

        assertBusinessException(
                () -> userService.createUser(user),
                ErrorCode.IDENTIFIER_EXISTS,
                "手机号已存在"
        );
        verify(userMapper, never()).insert(any());
    }

    @Test
    void shouldRejectExistingEmail() {
        User user = User.builder().email("USER@EXAMPLE.COM").nickname("测试用户").build();
        when(userMapper.existsByEmail("user@example.com")).thenReturn(true);

        assertBusinessException(
                () -> userService.createUser(user),
                ErrorCode.IDENTIFIER_EXISTS,
                "邮箱已存在"
        );
        verify(userMapper, never()).insert(any());
    }

    @Test
    void shouldRejectInvalidTagsJson() {
        User user = User.builder()
                .phone("13800138000")
                .nickname("测试用户")
                .tagsJson("{\"tag\":\"Java\"}")
                .build();

        assertBusinessException(
                () -> userService.createUser(user),
                ErrorCode.BAD_REQUEST,
                "用户标签必须是 JSON 数组"
        );
        verify(userMapper, never()).insert(any());
    }

    @Test
    void shouldConvertConcurrentDuplicateToBusinessException() {
        User user = User.builder().phone("13800138000").nickname("测试用户").build();
        when(userMapper.insert(any(User.class))).thenThrow(new DuplicateKeyException("duplicate"));

        assertBusinessException(
                () -> userService.createUser(user),
                ErrorCode.IDENTIFIER_EXISTS,
                "账号已存在"
        );
    }

    @Test
    void shouldRejectInsertWithoutGeneratedId() {
        User user = User.builder().phone("13800138000").nickname("测试用户").build();
        when(userMapper.insert(any(User.class))).thenReturn(1);

        assertBusinessException(
                () -> userService.createUser(user),
                ErrorCode.INTERNAL_ERROR,
                "用户创建失败"
        );
    }

    @Test
    void shouldUpdatePasswordHash() {
        when(userMapper.updatePasswordHash(1L, "$2a$12$encoded-password"))
                .thenReturn(1);

        userService.updatePasswordHash(1L, "$2a$12$encoded-password");

        verify(userMapper).updatePasswordHash(1L, "$2a$12$encoded-password");
    }

    @Test
    void shouldRejectPasswordUpdateForUnknownUser() {
        when(userMapper.updatePasswordHash(99L, "$2a$12$encoded-password"))
                .thenReturn(0);

        assertBusinessException(
                () -> userService.updatePasswordHash(99L, "$2a$12$encoded-password"),
                ErrorCode.RESOURCE_NOT_FOUND,
                "用户不存在"
        );
    }

    @Test
    void shouldRejectInvalidPasswordHash() {
        assertBusinessException(
                () -> userService.updatePasswordHash(1L, " "),
                ErrorCode.BAD_REQUEST,
                "密码哈希无效"
        );
        verify(userMapper, never()).updatePasswordHash(anyLong(), any());
    }

    private void assertBusinessException(
            Runnable action,
            ErrorCode expectedErrorCode,
            String expectedMessage
    ) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode);
                    assertThat(exception.getMessage()).isEqualTo(expectedMessage);
                });
    }
}
