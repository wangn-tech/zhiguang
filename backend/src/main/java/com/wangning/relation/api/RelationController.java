package com.wangning.relation.api;

import com.wangning.auth.token.JwtService;
import com.wangning.relation.api.dto.PublicProfileResponse;
import com.wangning.relation.api.dto.RelationCountersResponse;
import com.wangning.relation.api.dto.RelationStatusResponse;
import com.wangning.relation.service.RelationService;
import com.wangning.relation.service.RelationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户关注关系 REST API。
 */
@RestController
@RequestMapping("/api/v1/relation")
@RequiredArgsConstructor
public class RelationController {

    private final RelationService relationService;
    private final JwtService jwtService;

    /**
     * 关注目标用户。
     *
     * @param toUserId 被关注者用户 ID
     * @param jwt 已认证的 Access Token
     * @return 新建或恢复关系时为 {@code true}
     */
    @PostMapping("/follow")
    public boolean follow(
            @RequestParam("toUserId") long toUserId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return relationService.follow(jwtService.extractUserId(jwt), toUserId);
    }

    /**
     * 取消关注目标用户。
     *
     * @param toUserId 被取消关注者用户 ID
     * @param jwt 已认证的 Access Token
     * @return 成功取消有效关系时为 {@code true}
     */
    @PostMapping("/unfollow")
    public boolean unfollow(
            @RequestParam("toUserId") long toUserId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return relationService.unfollow(jwtService.extractUserId(jwt), toUserId);
    }

    /**
     * 查询当前用户与目标用户的双向关系。
     *
     * @param toUserId 目标用户 ID
     * @param jwt 已认证的 Access Token
     * @return 双向关系状态
     */
    @GetMapping("/status")
    public RelationStatusResponse status(
            @RequestParam("toUserId") long toUserId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        RelationStatus relationStatus = relationService.getStatus(jwtService.extractUserId(jwt), toUserId);
        return new RelationStatusResponse(
                relationStatus.following(),
                relationStatus.followedBy(),
                relationStatus.mutual()
        );
    }

    /**
     * 查询用户关注的公开资料列表。
     *
     * @param userId 被查询用户 ID
     * @param limit 最大返回数量
     * @param offset 偏移量
     * @param cursor 可选毫秒时间游标
     * @return 按最近关注时间倒序的公开资料
     */
    @GetMapping("/following")
    public List<PublicProfileResponse> following(
            @RequestParam("userId") long userId,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "cursor", required = false) Long cursor
    ) {
        return relationService.listFollowings(userId, limit, offset, cursor);
    }

    /**
     * 查询用户粉丝的公开资料列表。
     *
     * @param userId 被查询用户 ID
     * @param limit 最大返回数量
     * @param offset 偏移量
     * @param cursor 可选毫秒时间游标
     * @return 按最近关注时间倒序的公开资料
     */
    @GetMapping("/followers")
    public List<PublicProfileResponse> followers(
            @RequestParam("userId") long userId,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "cursor", required = false) Long cursor
    ) {
        return relationService.listFollowers(userId, limit, offset, cursor);
    }

    /**
     * 查询用户主页关系计数。
     *
     * @param userId 被查询用户 ID
     * @return 用户关系和互动计数
     */
    @GetMapping("/counter")
    public RelationCountersResponse counter(@RequestParam("userId") long userId) {
        return relationService.getCounters(userId);
    }
}
