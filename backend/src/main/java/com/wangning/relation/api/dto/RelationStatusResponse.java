package com.wangning.relation.api.dto;

/**
 * 当前用户相对目标用户的双向关系状态。
 *
 * @param following 当前用户是否关注目标用户
 * @param followedBy 目标用户是否关注当前用户
 * @param mutual 是否互相关注
 */
public record RelationStatusResponse(boolean following, boolean followedBy, boolean mutual) {
}
