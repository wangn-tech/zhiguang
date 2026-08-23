package com.wangning.profile.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.profile.api.dto.ProfilePatchRequest;
import com.wangning.profile.api.dto.ProfileResponse;
import com.wangning.profile.model.Gender;
import com.wangning.profile.service.impl.ProfileServiceImpl;
import com.wangning.user.domain.User;
import com.wangning.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

    @Mock
    private UserMapper userMapper;

    private ProfileServiceImpl profileService;

    @BeforeEach
    void setUp() {
        profileService = new ProfileServiceImpl(userMapper, new ObjectMapper());
    }

    @Test
    void shouldMergeSubmittedFieldsAndReturnLatestProfile() throws Exception {
        User current = currentUser();
        User updated = currentUser();
        updated.setNickname("更新后的昵称");
        updated.setBio(null);
        updated.setGender("FEMALE");
        updated.setTagsJson("[\"Java\",\"后端\"]");
        when(userMapper.findById(1L)).thenReturn(current, updated);
        when(userMapper.updateProfile(any(User.class))).thenReturn(1);
        ProfilePatchRequest request = new ProfilePatchRequest();
        request.setNickname(" 更新后的昵称 ");
        request.setBio(null);
        request.setGender(Gender.FEMALE);
        request.setTagJson("[\" Java \",\"Java\",\"后端\"]");

        ProfileResponse response = profileService.updateProfile(1L, request);

        ArgumentCaptor<User> profileCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateProfile(profileCaptor.capture());
        User merged = profileCaptor.getValue();
        assertThat(merged.getNickname()).isEqualTo("更新后的昵称");
        assertThat(merged.getBio()).isNull();
        assertThat(merged.getGender()).isEqualTo("FEMALE");
        assertThat(merged.getZgId()).isEqualTo("zg_1001");
        assertThat(merged.getBirthday()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(merged.getSchool()).isEqualTo("同济大学");
        assertThat(new ObjectMapper().readTree(merged.getTagsJson()))
                .isEqualTo(new ObjectMapper().readTree("[\"Java\",\"后端\"]"));
        assertThat(response.nickname()).isEqualTo("更新后的昵称");
        assertThat(response.gender()).isEqualTo(Gender.FEMALE);
        assertThat(response.phone()).isEqualTo("13800138000");
        assertThat(response.email()).isEqualTo("user@example.com");
    }

    @Test
    void shouldClearEveryNullableFieldWhenExplicitlyNull() {
        User current = currentUser();
        User cleared = currentUser();
        cleared.setBio(null);
        cleared.setGender(null);
        cleared.setBirthday(null);
        cleared.setZgId(null);
        cleared.setSchool(null);
        cleared.setTagsJson("[]");
        when(userMapper.findById(1L)).thenReturn(current, cleared);
        when(userMapper.updateProfile(any(User.class))).thenReturn(1);
        ProfilePatchRequest request = new ProfilePatchRequest();
        request.setBio(null);
        request.setGender(null);
        request.setBirthday(null);
        request.setZgId(null);
        request.setSchool(null);
        request.setTagJson(null);

        ProfileResponse response = profileService.updateProfile(1L, request);

        ArgumentCaptor<User> profileCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateProfile(profileCaptor.capture());
        User merged = profileCaptor.getValue();
        assertThat(merged.getNickname()).isEqualTo("测试用户");
        assertThat(merged.getBio()).isNull();
        assertThat(merged.getGender()).isNull();
        assertThat(merged.getBirthday()).isNull();
        assertThat(merged.getZgId()).isNull();
        assertThat(merged.getSchool()).isNull();
        assertThat(merged.getTagsJson()).isEqualTo("[]");
        assertThat(response.tagJson()).isEqualTo("[]");
    }

    @Test
    void shouldRejectMissingUser() {
        when(userMapper.findById(99L)).thenReturn(null);
        ProfilePatchRequest request = new ProfilePatchRequest();
        request.setNickname("新昵称");

        assertBusinessException(
                () -> profileService.updateProfile(99L, request),
                ErrorCode.RESOURCE_NOT_FOUND,
                "用户不存在"
        );
        verify(userMapper, never()).updateProfile(any());
    }

    @Test
    void shouldRejectEmptyOrNullRequest() {
        when(userMapper.findById(1L)).thenReturn(currentUser());

        assertBusinessException(
                () -> profileService.updateProfile(1L, new ProfilePatchRequest()),
                ErrorCode.BAD_REQUEST,
                "未提交任何更新字段"
        );
        assertBusinessException(
                () -> profileService.updateProfile(1L, null),
                ErrorCode.BAD_REQUEST,
                "未提交任何更新字段"
        );
        verify(userMapper, never()).updateProfile(any());
    }

    @Test
    void shouldRejectClearingOrBlankingNickname() {
        when(userMapper.findById(1L)).thenReturn(currentUser());
        ProfilePatchRequest nullNickname = new ProfilePatchRequest();
        nullNickname.setNickname(null);
        ProfilePatchRequest blankNickname = new ProfilePatchRequest();
        blankNickname.setNickname("   ");

        assertBusinessException(
                () -> profileService.updateProfile(1L, nullNickname),
                ErrorCode.BAD_REQUEST,
                "昵称不能为空"
        );
        assertBusinessException(
                () -> profileService.updateProfile(1L, blankNickname),
                ErrorCode.BAD_REQUEST,
                "昵称不能为空"
        );
        verify(userMapper, never()).updateProfile(any());
    }

    @Test
    void shouldRejectInvalidProfileFields() {
        when(userMapper.findById(1L)).thenReturn(currentUser());
        ProfilePatchRequest invalidBirthday = new ProfilePatchRequest();
        invalidBirthday.setBirthday(LocalDate.now().plusDays(1));
        ProfilePatchRequest longNickname = new ProfilePatchRequest();
        longNickname.setNickname("n".repeat(65));
        ProfilePatchRequest invalidZgId = new ProfilePatchRequest();
        invalidZgId.setZgId("非法");
        ProfilePatchRequest longBio = new ProfilePatchRequest();
        longBio.setBio("b".repeat(513));
        ProfilePatchRequest longSchool = new ProfilePatchRequest();
        longSchool.setSchool("s".repeat(129));

        assertErrorCode(() -> profileService.updateProfile(1L, invalidBirthday), ErrorCode.BAD_REQUEST);
        assertErrorCode(() -> profileService.updateProfile(1L, longNickname), ErrorCode.BAD_REQUEST);
        assertErrorCode(() -> profileService.updateProfile(1L, invalidZgId), ErrorCode.BAD_REQUEST);
        assertErrorCode(() -> profileService.updateProfile(1L, longBio), ErrorCode.BAD_REQUEST);
        assertErrorCode(() -> profileService.updateProfile(1L, longSchool), ErrorCode.BAD_REQUEST);
        verify(userMapper, never()).updateProfile(any());
    }

    @Test
    void shouldRejectInvalidTagStructures() {
        when(userMapper.findById(1L)).thenReturn(currentUser());
        ProfilePatchRequest objectTags = requestWithTags("{\"tag\":\"Java\"}");
        ProfilePatchRequest numericTags = requestWithTags("[\"Java\", 1]");
        ProfilePatchRequest blankTags = requestWithTags("[\"Java\", \"  \"]");
        ProfilePatchRequest malformedTags = requestWithTags("[");
        ProfilePatchRequest trailingTags = requestWithTags("[\"Java\"] true");

        assertErrorCode(() -> profileService.updateProfile(1L, objectTags), ErrorCode.BAD_REQUEST);
        assertErrorCode(() -> profileService.updateProfile(1L, numericTags), ErrorCode.BAD_REQUEST);
        assertErrorCode(() -> profileService.updateProfile(1L, blankTags), ErrorCode.BAD_REQUEST);
        assertErrorCode(() -> profileService.updateProfile(1L, malformedTags), ErrorCode.BAD_REQUEST);
        assertErrorCode(() -> profileService.updateProfile(1L, trailingTags), ErrorCode.BAD_REQUEST);
        verify(userMapper, never()).updateProfile(any());
    }

    @Test
    void shouldRejectExistingZgIdBeforeUpdate() {
        when(userMapper.findById(1L)).thenReturn(currentUser());
        when(userMapper.existsByZgIdExceptId("zg_taken", 1L)).thenReturn(true);
        ProfilePatchRequest request = new ProfilePatchRequest();
        request.setZgId("zg_taken");

        assertBusinessException(
                () -> profileService.updateProfile(1L, request),
                ErrorCode.ZGID_EXISTS,
                "知光号已存在"
        );
        verify(userMapper, never()).updateProfile(any());
    }

    @Test
    void shouldConvertConcurrentZgIdConflict() {
        when(userMapper.findById(1L)).thenReturn(currentUser());
        when(userMapper.updateProfile(any(User.class)))
                .thenThrow(new DuplicateKeyException("duplicate zg_id"));
        ProfilePatchRequest request = new ProfilePatchRequest();
        request.setZgId("zg_available");

        assertBusinessException(
                () -> profileService.updateProfile(1L, request),
                ErrorCode.ZGID_EXISTS,
                "知光号已存在"
        );
    }

    @Test
    void shouldRejectUnexpectedAffectedRowCounts() {
        when(userMapper.findById(1L)).thenReturn(currentUser());
        ProfilePatchRequest request = new ProfilePatchRequest();
        request.setNickname("新昵称");
        when(userMapper.updateProfile(any(User.class))).thenReturn(0, 2);

        assertBusinessException(
                () -> profileService.updateProfile(1L, request),
                ErrorCode.RESOURCE_NOT_FOUND,
                "用户不存在"
        );
        assertBusinessException(
                () -> profileService.updateProfile(1L, request),
                ErrorCode.INTERNAL_ERROR,
                "个人资料更新失败"
        );
    }

    @Test
    void shouldRejectInvalidUserIdWithoutDatabaseAccess() {
        ProfilePatchRequest request = new ProfilePatchRequest();
        request.setNickname("新昵称");

        assertBusinessException(
                () -> profileService.updateProfile(0L, request),
                ErrorCode.RESOURCE_NOT_FOUND,
                "用户不存在"
        );
        verify(userMapper, never()).findById(anyLong());
    }

    private ProfilePatchRequest requestWithTags(String tagsJson) {
        ProfilePatchRequest request = new ProfilePatchRequest();
        request.setTagJson(tagsJson);
        return request;
    }

    private User currentUser() {
        return User.builder()
                .id(1L)
                .phone("13800138000")
                .email("user@example.com")
                .passwordHash("password-hash")
                .nickname("测试用户")
                .avatar("https://example.com/avatar.png")
                .bio("个人简介")
                .zgId("zg_1001")
                .gender("UNKNOWN")
                .birthday(LocalDate.of(2000, 1, 1))
                .school("同济大学")
                .tagsJson("[\"Java\"]")
                .build();
    }

    private void assertErrorCode(Runnable action, ErrorCode expectedErrorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode));
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
