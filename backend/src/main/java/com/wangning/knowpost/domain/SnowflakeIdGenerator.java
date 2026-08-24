package com.wangning.knowpost.domain;

import java.util.function.LongSupplier;

/**
 * 线程安全的雪花 ID 生成器。
 *
 * <p>ID 由 41 位毫秒时间戳、5 位数据中心节点号、5 位工作节点号和 12 位毫秒内序列组成。
 * epoch 固定为 {@code 2024-01-01T00:00:00Z}，投入使用后不得修改。</p>
 */
public class SnowflakeIdGenerator {

    /** 2024-01-01T00:00:00Z 的毫秒时间戳。 */
    static final long EPOCH = 1_704_067_200_000L;

    private static final long MAX_NODE_ID = 31L;
    private static final long SEQUENCE_MASK = 4_095L;
    private static final int WORKER_ID_SHIFT = 12;
    private static final int DATACENTER_ID_SHIFT = 17;
    private static final int TIMESTAMP_SHIFT = 22;
    private static final long MAX_CLOCK_BACKWARD_MILLIS = 5L;

    private final long datacenterId;
    private final long workerId;
    private final LongSupplier clock;

    private long lastTimestamp = -1L;
    private long sequence;

    /**
     * 使用系统时钟创建生成器。
     *
     * @param datacenterId 数据中心节点号，范围为 0-31
     * @param workerId 工作节点号，范围为 0-31
     */
    public SnowflakeIdGenerator(long datacenterId, long workerId) {
        this(datacenterId, workerId, System::currentTimeMillis);
    }

    /**
     * 使用指定时钟创建生成器，仅用于本包测试。
     *
     * @param datacenterId 数据中心节点号，范围为 0-31
     * @param workerId 工作节点号，范围为 0-31
     * @param clock 返回当前毫秒时间戳的时钟
     */
    SnowflakeIdGenerator(long datacenterId, long workerId, LongSupplier clock) {
        validateNodeId(datacenterId, "datacenterId");
        validateNodeId(workerId, "workerId");
        if (clock == null) {
            throw new IllegalArgumentException("clock 不能为空");
        }
        this.datacenterId = datacenterId;
        this.workerId = workerId;
        this.clock = clock;
    }

    /**
     * 生成一个全局唯一且在当前实例内有序的 ID。
     *
     * @return 新生成的正整数 ID
     * @throws IllegalStateException 系统时间严重回拨、早于 epoch 或超过可表示范围时抛出
     */
    public synchronized long nextId() {
        long timestamp = currentTime();
        ensureTimestampInRange(timestamp);

        if (timestamp < lastTimestamp) {
            timestamp = recoverFromClockBackward(timestamp);
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /**
     * 尝试处理小幅时钟回拨。
     *
     * @param currentTimestamp 当前读取到的时间
     * @return 已追回的时间戳
     */
    private long recoverFromClockBackward(long currentTimestamp) {
        long offset = lastTimestamp - currentTimestamp;
        if (offset > MAX_CLOCK_BACKWARD_MILLIS) {
            throw new IllegalStateException("系统时钟回拨超过 " + MAX_CLOCK_BACKWARD_MILLIS + "ms");
        }

        try {
            Thread.sleep(offset);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待系统时钟恢复时被中断", exception);
        }

        long recoveredTimestamp = currentTime();
        if (recoveredTimestamp < lastTimestamp) {
            throw new IllegalStateException("系统时钟回拨后未恢复");
        }
        return recoveredTimestamp;
    }

    /**
     * 等待进入下一毫秒，避免单毫秒序列溢出后生成重复 ID。
     *
     * @param timestamp 已用尽序列的毫秒时间戳
     * @return 下一毫秒时间戳
     */
    private long waitNextMillis(long timestamp) {
        long nextTimestamp = currentTime();
        while (nextTimestamp <= timestamp) {
            Thread.onSpinWait();
            nextTimestamp = currentTime();
        }
        ensureTimestampInRange(nextTimestamp);
        return nextTimestamp;
    }

    /**
     * 读取当前时间。
     *
     * @return 当前毫秒时间戳
     */
    private long currentTime() {
        return clock.getAsLong();
    }

    /**
     * 校验节点号范围。
     *
     * @param nodeId 节点号
     * @param name 节点号名称
     */
    private static void validateNodeId(long nodeId, String name) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException(name + " 必须在 0-31 范围内");
        }
    }

    /**
     * 校验时间戳可由 41 位时间差表示。
     *
     * @param timestamp 当前毫秒时间戳
     */
    private static void ensureTimestampInRange(long timestamp) {
        long elapsed = timestamp - EPOCH;
        if (elapsed < 0 || elapsed > ((1L << 41) - 1)) {
            throw new IllegalStateException("当前时间超出雪花 ID 支持范围");
        }
    }
}
