package com.wangning.counter.schema;

/**
 * 互动位图分片计算工具。
 */
public final class BitmapShard {

    /** 每个 Redis 位图分片存储的用户位数。 */
    public static final long CHUNK_SIZE = 32_768L;

    private BitmapShard() {
    }

    /**
     * 计算用户所属的位图分片编号。
     *
     * @param userId 用户 ID
     * @return 分片编号
     * @throws IllegalArgumentException 用户 ID 非正数时抛出
     */
    public static long chunkOf(long userId) {
        validateUserId(userId);
        return userId / CHUNK_SIZE;
    }

    /**
     * 计算用户在所属分片中的位偏移。
     *
     * @param userId 用户 ID
     * @return 位偏移，范围为 0 至 {@value #CHUNK_SIZE} - 1
     * @throws IllegalArgumentException 用户 ID 非正数时抛出
     */
    public static long bitOf(long userId) {
        validateUserId(userId);
        return userId % CHUNK_SIZE;
    }

    private static void validateUserId(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
    }
}
