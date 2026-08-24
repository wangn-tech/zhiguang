package com.wangning.knowpost.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class SnowflakePropertiesTest {

    @Test
    void shouldAcceptNodeIdsWithinRange() {
        SnowflakeProperties properties = new SnowflakeProperties();
        properties.setDatacenterId(31L);
        properties.setWorkerId(0L);

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectMissingOrOutOfRangeNodeIds() {
        SnowflakeProperties missing = new SnowflakeProperties();
        missing.setWorkerId(0L);
        assertThatIllegalStateException().isThrownBy(missing::validate);

        SnowflakeProperties outOfRange = new SnowflakeProperties();
        outOfRange.setDatacenterId(0L);
        outOfRange.setWorkerId(32L);
        assertThatIllegalStateException().isThrownBy(outOfRange::validate);
    }
}
