package com.wangning.knowpost.domain;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SnowflakeIdGeneratorTest {

    @Test
    void shouldGenerateIncreasingIdsWithinSameMillisecond() {
        AtomicLong clock = new AtomicLong(SnowflakeIdGenerator.EPOCH + 1_000);
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(2, 3, clock::get);

        long first = generator.nextId();
        long second = generator.nextId();

        assertThat(second).isEqualTo(first + 1);
        assertThat((first >>> 17) & 31L).isEqualTo(2L);
        assertThat((first >>> 12) & 31L).isEqualTo(3L);
    }

    @Test
    void shouldGenerateDistinctIdsWhenTimeMovesForward() {
        AtomicLong clock = new AtomicLong(SnowflakeIdGenerator.EPOCH + 1_000);
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0, 0, clock::get);

        long first = generator.nextId();
        clock.incrementAndGet();
        long second = generator.nextId();

        assertThat(second).isGreaterThan(first);
        assertThat(second & 4_095L).isZero();
    }

    @Test
    void shouldRejectInvalidNodeIds() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SnowflakeIdGenerator(-1, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SnowflakeIdGenerator(0, 32));
    }
}
