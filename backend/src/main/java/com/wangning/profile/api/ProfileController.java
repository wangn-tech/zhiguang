package com.wangning.profile.api;

import com.wangning.auth.token.JwtService;
import com.wangning.profile.api.dto.ProfilePatchRequest;
import com.wangning.profile.api.dto.ProfileResponse;
import com.wangning.profile.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 当前用户个人资料 REST API。
 */
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final JwtService jwtService;

    /**
     * 局部更新当前登录用户资料。
     *
     * <p>用户身份只从已经通过校验的 Access Token 中取得，不接受客户端指定用户 ID。</p>
     *
     * @param jwt 已通过 Access Token 解码器校验的 JWT
     * @param request 资料局部更新请求
     * @return 更新后的完整资料
     */
    @PatchMapping
    public ProfileResponse updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfilePatchRequest request
    ) {
        long userId = jwtService.extractUserId(jwt);
        return profileService.updateProfile(userId, request);
    }

    /**
     * 上传并更新当前登录用户头像。
     *
     * <p>multipart 字段名固定为 {@code file}，用户身份只从已经通过校验的
     * Access Token 中取得。</p>
     *
     * @param jwt 已通过 Access Token 解码器校验的 JWT
     * @param file 头像文件
     * @return 更新头像后的完整资料
     */
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileResponse uploadAvatar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file
    ) {
        long userId = jwtService.extractUserId(jwt);
        return profileService.uploadAvatar(userId, file);
    }
}
