package com.wangning.counter.service.impl;

import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.counter.service.CounterActionResult;
import com.wangning.counter.service.CounterActionService;
import com.wangning.counter.service.CounterService;
import com.wangning.knowpost.domain.KnowPost;
import com.wangning.knowpost.mapper.KnowPostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 知文互动写服务实现。
 */
@Service
@RequiredArgsConstructor
public class CounterActionServiceImpl implements CounterActionService {

    private static final String KNOWPOST = "knowpost";
    private static final String STATUS_PUBLISHED = "published";
    private static final String VISIBILITY_PUBLIC = "public";

    private final CounterService counterService;
    private final KnowPostMapper knowPostMapper;

    /** {@inheritDoc} */
    @Override
    public CounterActionResult like(String entityType, String entityId, long userId) {
        validateInteractionTarget(entityType, entityId, userId);
        boolean changed = counterService.like(entityType, entityId, userId);
        return new CounterActionResult(changed, counterService.isLiked(entityType, entityId, userId));
    }

    /** {@inheritDoc} */
    @Override
    public CounterActionResult unlike(String entityType, String entityId, long userId) {
        validateInteractionTarget(entityType, entityId, userId);
        boolean changed = counterService.unlike(entityType, entityId, userId);
        return new CounterActionResult(changed, counterService.isLiked(entityType, entityId, userId));
    }

    /** {@inheritDoc} */
    @Override
    public CounterActionResult fav(String entityType, String entityId, long userId) {
        validateInteractionTarget(entityType, entityId, userId);
        boolean changed = counterService.fav(entityType, entityId, userId);
        return new CounterActionResult(changed, counterService.isFaved(entityType, entityId, userId));
    }

    /** {@inheritDoc} */
    @Override
    public CounterActionResult unfav(String entityType, String entityId, long userId) {
        validateInteractionTarget(entityType, entityId, userId);
        boolean changed = counterService.unfav(entityType, entityId, userId);
        return new CounterActionResult(changed, counterService.isFaved(entityType, entityId, userId));
    }

    private void validateInteractionTarget(String entityType, String entityId, long userId) {
        if (!KNOWPOST.equals(entityType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持知文互动");
        }
        long postId = parsePostId(entityId);
        KnowPost knowPost = knowPostMapper.findById(postId);
        if (knowPost == null || !STATUS_PUBLISHED.equals(knowPost.getStatus())
                || !VISIBILITY_PUBLIC.equals(knowPost.getVisible())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "知文不存在或不可互动");
        }
        if (knowPost.getCreatorId() != null && knowPost.getCreatorId() == userId) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能与自己的知文互动");
        }
    }

    private long parsePostId(String entityId) {
        try {
            long postId = Long.parseLong(entityId);
            if (postId <= 0) {
                throw new NumberFormatException("post id must be positive");
            }
            return postId;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "实体 ID 必须为正整数");
        }
    }
}
