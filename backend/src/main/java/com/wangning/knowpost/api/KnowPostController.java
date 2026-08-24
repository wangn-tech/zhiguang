package com.wangning.knowpost.api;

import com.wangning.auth.token.JwtService;
import com.wangning.knowpost.api.dto.FeedPageResponse;
import com.wangning.knowpost.api.dto.KnowPostContentConfirmRequest;
import com.wangning.knowpost.api.dto.KnowPostDetailResponse;
import com.wangning.knowpost.api.dto.KnowPostDraftCreateResponse;
import com.wangning.knowpost.api.dto.KnowPostPatchRequest;
import com.wangning.knowpost.api.dto.KnowPostTopPatchRequest;
import com.wangning.knowpost.api.dto.KnowPostVisibilityPatchRequest;
import com.wangning.knowpost.service.KnowPostFeedService;
import com.wangning.knowpost.service.KnowPostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知文 HTTP 接口。
 */
@RestController
@RequestMapping("/api/v1/knowposts")
@Validated
@RequiredArgsConstructor
public class KnowPostController {

    private final KnowPostService knowPostService;
    private final KnowPostFeedService knowPostFeedService;
    private final JwtService jwtService;

    /**
     * 创建当前用户的图文草稿。
     *
     * @param jwt 已验证的 Access Token
     * @return 新草稿 ID
     */
    @PostMapping("/drafts")
    public KnowPostDraftCreateResponse createDraft(@AuthenticationPrincipal Jwt jwt) {
        long userId = jwtService.extractUserId(jwt);
        return new KnowPostDraftCreateResponse(String.valueOf(knowPostService.createDraft(userId)));
    }

    /**
     * 确认正文已上传至 OSS。
     *
     * @param id 知文 ID
     * @param request 正文确认信息
     * @param jwt 已验证的 Access Token
     * @return 空响应
     */
    @PostMapping("/{id}/content/confirm")
    public ResponseEntity<Void> confirmContent(
            @PathVariable long id,
            @Valid @RequestBody KnowPostContentConfirmRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        knowPostService.confirmContent(
                jwtService.extractUserId(jwt),
                id,
                request.objectKey(),
                request.etag(),
                request.size(),
                request.sha256()
        );
        return ResponseEntity.noContent().build();
    }

    /**
     * 更新知文元数据。
     *
     * @param id 知文 ID
     * @param request 局部更新请求
     * @param jwt 已验证的 Access Token
     * @return 空响应
     */
    @PatchMapping("/{id}")
    public ResponseEntity<Void> patchMetadata(
            @PathVariable long id,
            @Valid @RequestBody KnowPostPatchRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        knowPostService.updateMetadata(
                jwtService.extractUserId(jwt),
                id,
                request.title(),
                request.tagId(),
                request.tags(),
                request.imgUrls(),
                request.visible(),
                request.isTop(),
                request.description()
        );
        return ResponseEntity.noContent().build();
    }

    /**
     * 发布知文。
     *
     * @param id 知文 ID
     * @param jwt 已验证的 Access Token
     * @return 空响应
     */
    @PostMapping("/{id}/publish")
    public ResponseEntity<Void> publish(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        knowPostService.publish(jwtService.extractUserId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 设置知文置顶状态。
     *
     * @param id 知文 ID
     * @param request 置顶请求
     * @param jwt 已验证的 Access Token
     * @return 空响应
     */
    @PatchMapping("/{id}/top")
    public ResponseEntity<Void> patchTop(
            @PathVariable long id,
            @Valid @RequestBody KnowPostTopPatchRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        knowPostService.updateTop(jwtService.extractUserId(jwt), id, request.isTop());
        return ResponseEntity.noContent().build();
    }

    /**
     * 设置知文可见性。
     *
     * @param id 知文 ID
     * @param request 可见性请求
     * @param jwt 已验证的 Access Token
     * @return 空响应
     */
    @PatchMapping("/{id}/visibility")
    public ResponseEntity<Void> patchVisibility(
            @PathVariable long id,
            @Valid @RequestBody KnowPostVisibilityPatchRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        knowPostService.updateVisibility(jwtService.extractUserId(jwt), id, request.visible());
        return ResponseEntity.noContent().build();
    }

    /**
     * 软删除知文。
     *
     * @param id 知文 ID
     * @param jwt 已验证的 Access Token
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        knowPostService.delete(jwtService.extractUserId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 查询公开 Feed。
     *
     * @param page 页码
     * @param size 页大小
     * @param jwt 可选的已验证 Access Token
     * @return 分页 Feed
     */
    @GetMapping("/feed")
    public FeedPageResponse feed(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = jwt == null ? null : jwtService.extractUserId(jwt);
        return knowPostFeedService.getPublicFeed(page, size, userId);
    }

    /**
     * 查询当前用户已发布的知文。
     *
     * @param page 页码
     * @param size 页大小
     * @param jwt 已验证的 Access Token
     * @return 分页 Feed
     */
    @GetMapping("/mine")
    public FeedPageResponse mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return knowPostFeedService.getMyPublished(jwtService.extractUserId(jwt), page, size);
    }

    /**
     * 查询知文详情。
     *
     * @param id 知文 ID
     * @param jwt 可选的已验证 Access Token
     * @return 知文详情
     */
    @GetMapping("/detail/{id}")
    public KnowPostDetailResponse detail(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt == null ? null : jwtService.extractUserId(jwt);
        return knowPostService.getDetail(id, userId);
    }
}
