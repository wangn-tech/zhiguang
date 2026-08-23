package com.wangning.profile.api;

import com.wangning.auth.config.AuthProperties;
import com.wangning.auth.config.SecurityConfig;
import com.wangning.auth.token.JwtService;
import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.profile.api.dto.ProfilePatchRequest;
import com.wangning.profile.api.dto.ProfileResponse;
import com.wangning.profile.model.Gender;
import com.wangning.profile.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfileController.class)
@Import({SecurityConfig.class, ProfileControllerTest.TestConfig.class})
class ProfileControllerTest {

    private static final String PROFILE_PATH = "/api/v1/profile";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProfileService profileService;

    @MockBean
    private JwtService jwtService;

    @MockBean(name = "accessJwtDecoder")
    private JwtDecoder accessJwtDecoder;

    @Test
    void shouldRequireAccessToken() throws Exception {
        mockMvc.perform(patch(PROFILE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"新昵称\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value(PROFILE_PATH));

        verify(profileService, never()).updateProfile(anyLong(), any());
    }

    @Test
    void shouldUpdateAuthenticatedUserAndIgnoreClientUserId() throws Exception {
        when(jwtService.extractUserId(any(Jwt.class))).thenReturn(42L);
        when(profileService.updateProfile(eq(42L), any(ProfilePatchRequest.class)))
                .thenReturn(profileResponse());

        mockMvc.perform(patch(PROFILE_PATH)
                        .with(accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 999,
                                  "nickname": "新昵称",
                                  "gender": "FEMALE",
                                  "birthday": "2000-01-01",
                                  "zgId": "zg_42",
                                  "tagJson": "[\\\"Java\\\"]"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.nickname").value("新昵称"))
                .andExpect(jsonPath("$.gender").value("FEMALE"))
                .andExpect(jsonPath("$.birthday").value("2000-01-01"))
                .andExpect(jsonPath("$.zgId").value("zg_42"))
                .andExpect(jsonPath("$.phone").value("13800138000"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        ArgumentCaptor<ProfilePatchRequest> requestCaptor =
                ArgumentCaptor.forClass(ProfilePatchRequest.class);
        verify(profileService).updateProfile(eq(42L), requestCaptor.capture());
        ProfilePatchRequest request = requestCaptor.getValue();
        assertThat(request.getNickname()).isEqualTo("新昵称");
        assertThat(request.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(request.isNicknamePresent()).isTrue();
        assertThat(request.isGenderPresent()).isTrue();
        assertThat(request.isBioPresent()).isFalse();
    }

    @Test
    void shouldPreserveExplicitNullFieldsForService() throws Exception {
        when(jwtService.extractUserId(any(Jwt.class))).thenReturn(42L);
        when(profileService.updateProfile(eq(42L), any(ProfilePatchRequest.class)))
                .thenReturn(profileResponse());

        mockMvc.perform(patch(PROFILE_PATH)
                        .with(accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bio": null,
                                  "gender": null,
                                  "birthday": null,
                                  "zgId": null,
                                  "school": null,
                                  "tagJson": null
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<ProfilePatchRequest> requestCaptor =
                ArgumentCaptor.forClass(ProfilePatchRequest.class);
        verify(profileService).updateProfile(eq(42L), requestCaptor.capture());
        ProfilePatchRequest request = requestCaptor.getValue();
        assertThat(request.isNicknamePresent()).isFalse();
        assertThat(request.isBioPresent()).isTrue();
        assertThat(request.isGenderPresent()).isTrue();
        assertThat(request.isBirthdayPresent()).isTrue();
        assertThat(request.isZgIdPresent()).isTrue();
        assertThat(request.isSchoolPresent()).isTrue();
        assertThat(request.isTagJsonPresent()).isTrue();
        assertThat(request.getBio()).isNull();
        assertThat(request.getBirthday()).isNull();
    }

    @Test
    void shouldReturnBadRequestForEmptyPatch() throws Exception {
        when(jwtService.extractUserId(any(Jwt.class))).thenReturn(42L);
        when(profileService.updateProfile(eq(42L), any(ProfilePatchRequest.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        "未提交任何更新字段"
                ));

        mockMvc.perform(patch(PROFILE_PATH)
                        .with(accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("未提交任何更新字段"));
    }

    @Test
    void shouldRejectInvalidFieldConstraints() throws Exception {
        mockMvc.perform(patch(PROFILE_PATH)
                        .with(accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "%s"
                                }
                                """.formatted("n".repeat(65))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("昵称长度需在 1-64 之间"));

        verify(profileService, never()).updateProfile(anyLong(), any());
    }

    @Test
    void shouldRejectUnknownGender() throws Exception {
        mockMvc.perform(patch(PROFILE_PATH)
                        .with(accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gender\":\"INVALID\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("请求参数错误"));

        verify(profileService, never()).updateProfile(anyLong(), any());
    }

    @Test
    void shouldUploadAvatarForAuthenticatedUser() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "avatar.png",
                        MediaType.IMAGE_PNG_VALUE,
                        new byte[]{1, 2, 3}
                );
        when(jwtService.extractUserId(any(Jwt.class))).thenReturn(42L);
        when(profileService.uploadAvatar(eq(42L), any(MultipartFile.class)))
                .thenReturn(profileResponse());

        mockMvc.perform(multipart(PROFILE_PATH + "/avatar")
                        .file(file)
                        .with(accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.avatar")
                        .value("https://static.example.com/avatars/42/avatar.png"));

        ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(profileService).uploadAvatar(eq(42L), fileCaptor.capture());
        assertThat(fileCaptor.getValue().getOriginalFilename()).isEqualTo("avatar.png");
        assertThat(fileCaptor.getValue().getContentType()).isEqualTo(MediaType.IMAGE_PNG_VALUE);
    }

    @Test
    void shouldRequireAccessTokenForAvatarUpload() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "avatar.png",
                        MediaType.IMAGE_PNG_VALUE,
                        new byte[]{1}
                );

        mockMvc.perform(multipart(PROFILE_PATH + "/avatar").file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(profileService, never()).uploadAvatar(anyLong(), any());
    }

    @Test
    void shouldRejectAvatarRequestWithoutFilePart() throws Exception {
        mockMvc.perform(multipart(PROFILE_PATH + "/avatar")
                        .with(accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("上传文件缺失、格式错误或大小超过限制"));

        verify(profileService, never()).uploadAvatar(anyLong(), any());
    }

    @Test
    void shouldNotExposeDeferredProfileGetEndpoint() throws Exception {

        mockMvc.perform(get(PROFILE_PATH)
                        .with(accessToken()))
                .andExpect(status().isMethodNotAllowed());
    }

    /**
     * 创建带有用户身份的 Access Token 测试处理器。
     *
     * @return JWT 请求处理器
     */
    private org.springframework.test.web.servlet.request.RequestPostProcessor accessToken() {
        return jwt().jwt(jwt -> jwt
                .subject("42")
                .claim("uid", 42L)
                .claim("token_type", "access"));
    }

    /**
     * 创建个人资料响应。
     *
     * @return 个人资料响应
     */
    private ProfileResponse profileResponse() {
        return new ProfileResponse(
                42L,
                "新昵称",
                "https://static.example.com/avatars/42/avatar.png",
                "个人简介",
                "zg_42",
                Gender.FEMALE,
                LocalDate.of(2000, 1, 1),
                "同济大学",
                "13800138000",
                "user@example.com",
                "[\"Java\"]"
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {

        /**
         * 提供 Web 安全测试使用的默认认证配置。
         *
         * @return 认证配置
         */
        @Bean
        AuthProperties authProperties() {
            return new AuthProperties();
        }
    }
}
