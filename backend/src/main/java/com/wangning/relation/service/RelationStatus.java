package com.wangning.relation.service;

/**
 * 当前用户与目标用户之间的双向关系状态。
 *
 * @param following 当前用户是否关注目标用户
 * @param followedBy 目标用户是否关注当前用户
 * @param mutual 是否互相关注
 */
public record RelationStatus(boolean following, boolean followedBy, boolean mutual) {
}
