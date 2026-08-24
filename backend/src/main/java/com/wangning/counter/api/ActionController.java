package com.wangning.counter.api;

import com.wangning.auth.token.JwtService;
import com.wangning.counter.api.dto.ActionRequest;
import com.wangning.counter.service.CounterActionResult;
import com.wangning.counter.service.CounterActionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 知文点赞、收藏 HTTP 接口。
 *
 * <p>接口响应沿用前端既有契约：点赞操作返回 {@code liked}，收藏操作返回 {@code faved}。</p>
 */
@RestController
@RequestMapping("/api/v1/action")
@RequiredArgsConstructor
public class ActionController {

    private final CounterActionService counterActionService;
    private final JwtService jwtService;

    /**
     * 点赞知文。
     *
     * @param request 互动目标
     * @param jwt 已验证的 Access Token
     * @return 是否变更及点赞后的状态
     */
    @PostMapping("/like")
    public Map<String, Object> like(
            @Valid @RequestBody ActionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        CounterActionResult result = counterActionService.like(
                request.getEntityType(), request.getEntityId(), jwtService.extractUserId(jwt)
        );
        return Map.of("changed", result.changed(), "liked", result.active());
    }

    /**
     * 取消点赞知文。
     *
     * @param request 互动目标
     * @param jwt 已验证的 Access Token
     * @return 是否变更及取消后的状态
     */
    @PostMapping("/unlike")
    public Map<String, Object> unlike(
            @Valid @RequestBody ActionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        CounterActionResult result = counterActionService.unlike(
                request.getEntityType(), request.getEntityId(), jwtService.extractUserId(jwt)
        );
        return Map.of("changed", result.changed(), "liked", result.active());
    }

    /**
     * 收藏知文。
     *
     * @param request 互动目标
     * @param jwt 已验证的 Access Token
     * @return 是否变更及收藏后的状态
     */
    @PostMapping("/fav")
    public Map<String, Object> fav(
            @Valid @RequestBody ActionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        CounterActionResult result = counterActionService.fav(
                request.getEntityType(), request.getEntityId(), jwtService.extractUserId(jwt)
        );
        return Map.of("changed", result.changed(), "faved", result.active());
    }

    /**
     * 取消收藏知文。
     *
     * @param request 互动目标
     * @param jwt 已验证的 Access Token
     * @return 是否变更及取消后的状态
     */
    @PostMapping("/unfav")
    public Map<String, Object> unfav(
            @Valid @RequestBody ActionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        CounterActionResult result = counterActionService.unfav(
                request.getEntityType(), request.getEntityId(), jwtService.extractUserId(jwt)
        );
        return Map.of("changed", result.changed(), "faved", result.active());
    }
}
