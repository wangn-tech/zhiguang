package com.wangning.counter.service;

/**
 * 一次点赞或收藏操作的状态结果。
 *
 * @param changed 本次请求是否实际改变互动状态
 * @param active 操作完成后的目标状态
 */
public record CounterActionResult(boolean changed, boolean active) {
}
