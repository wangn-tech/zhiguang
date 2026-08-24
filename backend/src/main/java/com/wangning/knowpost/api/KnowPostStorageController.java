package com.wangning.knowpost.api;

import com.wangning.auth.token.JwtService;
import com.wangning.knowpost.api.dto.StoragePresignRequest;
import com.wangning.knowpost.api.dto.StoragePresignResponse;
import com.wangning.knowpost.service.KnowPostUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知文浏览器直传 OSS 的 HTTP 接口。
 */
@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
public class KnowPostStorageController {

    private final KnowPostUploadService knowPostUploadService;
    private final JwtService jwtService;

    /**
     * 获取正文或图片的 PUT 预签名地址。
     *
     * @param request 预签名请求
     * @param jwt 已验证的 Access Token
     * @return PUT 预签名信息
     */
    @PostMapping("/presign")
    public StoragePresignResponse presign(
            @Valid @RequestBody StoragePresignRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return knowPostUploadService.presignUpload(jwtService.extractUserId(jwt), request);
    }
}
