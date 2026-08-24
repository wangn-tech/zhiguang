package com.wangning.relation.processor;

import com.wangning.relation.config.RelationEventProperties;
import com.wangning.relation.domain.UserRelation;
import com.wangning.relation.event.RelationEvent;
import com.wangning.relation.mapper.RelationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RelationEventProcessorTest {

    @Mock
    private RelationMapper relationMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private RelationEventProcessor processor;

    @BeforeEach
    void setUp() {
        RelationEventProperties properties = new RelationEventProperties();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        processor = new RelationEventProcessor(relationMapper, redisTemplate, properties);
    }

    @Test
    void shouldCreateFollowerAndRefreshRelationshipCaches() {
        when(valueOperations.setIfAbsent("relation:event:101", "1", Duration.ofDays(7))).thenReturn(true);

        processor.process(101L, new RelationEvent("FollowCreated", 1L, 2L, 99L));

        ArgumentCaptor<UserRelation> relationCaptor = ArgumentCaptor.forClass(UserRelation.class);
        verify(relationMapper).upsertFollower(relationCaptor.capture());
        assertThat(relationCaptor.getValue())
                .extracting(UserRelation::getId, UserRelation::getFromUserId, UserRelation::getToUserId,
                        UserRelation::getActive)
                .containsExactly(99L, 1L, 2L, true);
        assertThat(relationCaptor.getValue().getCreatedAt()).isNotNull();
        verify(zSetOperations).add(eq("relation:following:1"), eq("2"), any(Double.class));
        verify(zSetOperations).add(eq("relation:follower:2"), eq("1"), any(Double.class));
        verify(redisTemplate).expire("relation:following:1", Duration.ofHours(2));
        verify(redisTemplate).expire("relation:follower:2", Duration.ofHours(2));
    }

    @Test
    void shouldCancelFollowerAndRemoveRelationshipCaches() {
        when(valueOperations.setIfAbsent("relation:event:102", "1", Duration.ofDays(7))).thenReturn(true);

        processor.process(102L, new RelationEvent("FollowCanceled", 1L, 2L, 99L));

        verify(relationMapper).deactivateFollower(eq(2L), eq(1L), any());
        verify(zSetOperations).remove("relation:following:1", "2");
        verify(zSetOperations).remove("relation:follower:2", "1");
    }

    @Test
    void shouldSkipAlreadyProcessedOutboxEvent() {
        when(valueOperations.setIfAbsent("relation:event:101", "1", Duration.ofDays(7))).thenReturn(false);

        processor.process(101L, new RelationEvent("FollowCreated", 1L, 2L, 99L));

        verify(relationMapper, never()).upsertFollower(any());
        verify(zSetOperations, never()).add(any(), any(), any(Double.class));
    }

    @Test
    void shouldRemoveDedupKeyWhenProcessingFails() {
        when(valueOperations.setIfAbsent("relation:event:101", "1", Duration.ofDays(7))).thenReturn(true);
        when(relationMapper.upsertFollower(any())).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> processor.process(101L, new RelationEvent("FollowCreated", 1L, 2L, 99L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(redisTemplate).delete("relation:event:101");
    }

    @Test
    void shouldRejectUnknownOrInvalidEvent() {
        assertThatThrownBy(() -> processor.process(0L, new RelationEvent("FollowCreated", 1L, 2L, 99L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> processor.process(1L, new RelationEvent("Unknown", 1L, 2L, 99L)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(redisTemplate, never()).opsForValue();
    }
}
