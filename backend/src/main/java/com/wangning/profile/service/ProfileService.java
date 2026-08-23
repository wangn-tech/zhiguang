package com.wangning.profile.service;

import com.wangning.profile.api.dto.ProfilePatchRequest;
import com.wangning.profile.api.dto.ProfileResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 当前用户个人资料服务。
 */
public interface ProfileService {

    /**
     * 局部更新当前用户资料并返回最新快照。
     *
     * @param userId 当前用户 ID
     * @param request 资料局部更新请求
     * @return 更新后的完整资料
     */
    ProfileResponse updateProfile(long userId, ProfilePatchRequest request);

    /**
     * 上传并更新当前用户头像。
     *
     * @param userId 当前用户 ID
     * @param file 头像文件
     * @return 更新头像后的完整资料
     */
    ProfileResponse uploadAvatar(long userId, MultipartFile file);
}
