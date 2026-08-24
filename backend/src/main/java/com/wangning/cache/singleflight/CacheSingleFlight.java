package com.wangning.cache.singleflight;

import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 同一应用实例内缓存回源的 singleflight 协调器。
 *
 * <p>同一个缓存键同时未命中时，首个调用方负责执行回源函数，其余调用方等待同一个
 * {@link CompletableFuture}。任务完成（包括异常）后立即移除，以便后续缓存失效时可以重新回源。</p>
 *
 * <p>该组件只解决单实例内的缓存击穿；它不持有 Redis 分布式锁，也不会改变缓存故障时由业务层
 * 回源 MySQL 的降级语义。</p>
 */
@Component
public class CacheSingleFlight {

    private final ConcurrentHashMap<String, CompletableFuture<Object>> flights = new ConcurrentHashMap<>();

    /**
     * 合并同一键的并发回源请求。
     *
     * @param key 已带业务命名空间的缓存键
     * @param loader 仅由 leader 执行一次的回源函数
     * @param <T> 回源结果类型
     * @return leader 的回源结果，或等待 leader 完成后取得的同一结果
     */
    @SuppressWarnings("unchecked")
    public <T> T execute(String key, Supplier<T> loader) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(loader, "loader must not be null");

        CompletableFuture<Object> created = new CompletableFuture<>();
        CompletableFuture<Object> existing = flights.putIfAbsent(key, created);
        if (existing != null) {
            try {
                return (T) existing.join();
            } catch (CompletionException exception) {
                throw rethrow(exception.getCause());
            }
        }

        try {
            T value = loader.get();
            created.complete(value);
            return value;
        } catch (RuntimeException exception) {
            created.completeExceptionally(exception);
            throw exception;
        } catch (Error error) {
            created.completeExceptionally(error);
            throw error;
        } finally {
            flights.remove(key, created);
        }
    }

    private RuntimeException rethrow(Throwable cause) {
        if (cause instanceof RuntimeException exception) {
            return exception;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("缓存回源任务异常结束", cause);
    }
}
