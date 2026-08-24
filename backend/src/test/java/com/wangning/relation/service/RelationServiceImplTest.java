package com.wangning.relation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.relation.domain.UserRelation;
import com.wangning.relation.domain.RelationListItem;
import com.wangning.relation.mapper.RelationMapper;
import com.wangning.relation.outbox.OutboxMapper;
import com.wangning.relation.outbox.OutboxRecord;
import com.wangning.relation.api.dto.PublicProfileResponse;
import com.wangning.relation.api.dto.RelationCountersResponse;
import com.wangning.relation.service.impl.RelationServiceImpl;
import com.wangning.user.domain.User;
import com.wangning.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RelationServiceImplTest {

    @Mock
    private RelationMapper relationMapper;

    @Mock
    private OutboxMapper outboxMapper;

    @Mock
    private UserService userService;

    private RelationService relationService;

    @BeforeEach
    void setUp() {
        relationService = new RelationServiceImpl(
                relationMapper,
                outboxMapper,
                userService,
                new ObjectMapper()
        );
        lenient().when(userService.findById(2L)).thenReturn(Optional.of(User.builder().id(2L).build()));
    }

    @Test
    void shouldFollowAndWriteOutboxInSameBusinessOperation() throws Exception {
        when(relationMapper.insertFollowingIgnore(any(UserRelation.class))).thenReturn(1);
        when(relationMapper.findFollowing(1L, 2L))
                .thenReturn(UserRelation.builder().id(100L).fromUserId(1L).toUserId(2L).active(true).build());
        when(outboxMapper.insert(any(OutboxRecord.class))).thenReturn(1);

        assertThat(relationService.follow(1L, 2L)).isTrue();

        ArgumentCaptor<UserRelation> relationCaptor = ArgumentCaptor.forClass(UserRelation.class);
        ArgumentCaptor<OutboxRecord> outboxCaptor = ArgumentCaptor.forClass(OutboxRecord.class);
        verify(relationMapper).insertFollowingIgnore(relationCaptor.capture());
        verify(outboxMapper).insert(outboxCaptor.capture());

        UserRelation relation = relationCaptor.getValue();
        OutboxRecord event = outboxCaptor.getValue();
        assertThat(relation.getId()).isPositive();
        assertThat(relation.getFromUserId()).isEqualTo(1L);
        assertThat(relation.getToUserId()).isEqualTo(2L);
        assertThat(event.getId()).isPositive().isNotEqualTo(relation.getId());
        assertThat(event.getAggregateType()).isEqualTo("following");
        assertThat(event.getAggregateId()).isEqualTo(100L);
        assertThat(event.getType()).isEqualTo("FollowCreated");
        assertThat(new ObjectMapper().readTree(event.getPayload()))
                .isEqualTo(new ObjectMapper().readTree("""
                        {"type":"FollowCreated","fromUserId":1,"toUserId":2,"relationId":100}
                        """));
    }

    @Test
    void shouldTreatExistingFollowAsIdempotentAndNotWriteOutbox() {
        when(relationMapper.insertFollowingIgnore(any(UserRelation.class))).thenReturn(0);
        when(relationMapper.reactivateFollowing(any(Long.class), any(Long.class), any(), any())).thenReturn(0);

        assertThat(relationService.follow(1L, 2L)).isFalse();

        verify(relationMapper, never()).findFollowing(1L, 2L);
        verify(outboxMapper, never()).insert(any());
    }

    @Test
    void shouldUnfollowAndWriteCancellationEvent() throws Exception {
        when(relationMapper.deactivateFollowing(any(Long.class), any(Long.class), any()))
                .thenReturn(1);
        when(relationMapper.findFollowing(1L, 2L))
                .thenReturn(UserRelation.builder().id(100L).fromUserId(1L).toUserId(2L).active(false).build());
        when(outboxMapper.insert(any(OutboxRecord.class))).thenReturn(1);

        assertThat(relationService.unfollow(1L, 2L)).isTrue();

        ArgumentCaptor<OutboxRecord> outboxCaptor = ArgumentCaptor.forClass(OutboxRecord.class);
        verify(outboxMapper).insert(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getType()).isEqualTo("FollowCanceled");
        assertThat(new ObjectMapper().readTree(outboxCaptor.getValue().getPayload()))
                .isEqualTo(new ObjectMapper().readTree("""
                        {"type":"FollowCanceled","fromUserId":1,"toUserId":2,"relationId":100}
                        """));
    }

    @Test
    void shouldRejectSelfFollowAndUnknownTarget() {
        assertErrorCode(() -> relationService.follow(1L, 1L), ErrorCode.BAD_REQUEST);

        when(userService.findById(99L)).thenReturn(Optional.empty());
        assertErrorCode(() -> relationService.follow(1L, 99L), ErrorCode.RESOURCE_NOT_FOUND);

        verify(relationMapper, never()).insertFollowingIgnore(any());
        verify(outboxMapper, never()).insert(any());
    }

    @Test
    void shouldReturnBidirectionalStatus() {
        when(relationMapper.existsFollowing(1L, 2L)).thenReturn(true);
        when(relationMapper.existsFollowing(2L, 1L)).thenReturn(false);

        RelationStatus status = relationService.getStatus(1L, 2L);

        assertThat(status.following()).isTrue();
        assertThat(status.followedBy()).isFalse();
        assertThat(status.mutual()).isFalse();
    }

    @Test
    void shouldFailBusinessTransactionWhenOutboxCannotBeSaved() {
        when(relationMapper.insertFollowingIgnore(any(UserRelation.class))).thenReturn(1);
        when(relationMapper.findFollowing(1L, 2L))
                .thenReturn(UserRelation.builder().id(100L).build());
        when(outboxMapper.insert(any(OutboxRecord.class))).thenReturn(0);

        assertThatThrownBy(() -> relationService.follow(1L, 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Outbox 事件写入失败");
    }

    @Test
    void shouldReturnPublicProfilesInRelationOrder() {
        when(relationMapper.listFollowings(1L, 100, 0)).thenReturn(List.of(
                RelationListItem.builder().userId(20L).createdAt(Instant.parse("2026-08-24T10:00:00Z")).build(),
                RelationListItem.builder().userId(10L).createdAt(Instant.parse("2026-08-24T09:00:00Z")).build()
        ));
        when(userService.listByIds(List.of(20L, 10L))).thenReturn(List.of(
                publicUser(10L, "较早关注的用户"),
                publicUser(20L, "最近关注的用户")
        ));

        List<PublicProfileResponse> profiles = relationService.listFollowings(1L, 200, -1, null);

        assertThat(profiles)
                .extracting(PublicProfileResponse::id)
                .containsExactly(20L, 10L);
        assertThat(profiles.getFirst().nickname()).isEqualTo("最近关注的用户");
        verify(relationMapper).listFollowings(1L, 100, 0);
    }

    @Test
    void shouldUseCursorForFollowerList() {
        Instant cursor = Instant.parse("2026-08-24T10:00:00Z");
        when(relationMapper.listFollowersBefore(1L, cursor, 20)).thenReturn(List.of());

        assertThat(relationService.listFollowers(1L, 20, 8, cursor.toEpochMilli())).isEmpty();

        verify(relationMapper).listFollowersBefore(1L, cursor, 20);
        verify(relationMapper, never()).listFollowers(any(Long.class), any(Integer.class), any(Integer.class));
    }

    @Test
    void shouldReturnRelationCountersWithPendingInteractionCountsAsZero() {
        when(relationMapper.countFollowings(1L)).thenReturn(12L);
        when(relationMapper.countFollowers(1L)).thenReturn(34L);

        RelationCountersResponse counters = relationService.getCounters(1L);

        assertThat(counters).isEqualTo(new RelationCountersResponse(12L, 34L, 0L, 0L, 0L));
    }

    private User publicUser(long id, String nickname) {
        return User.builder()
                .id(id)
                .nickname(nickname)
                .avatar("https://static.example.com/avatars/" + id + ".png")
                .bio("个人简介")
                .zgId("zg_" + id)
                .gender("UNKNOWN")
                .birthday(LocalDate.of(2000, 1, 1))
                .school("同济大学")
                .tagsJson("[\"Java\"]")
                .build();
    }

    private void assertErrorCode(Runnable runnable, ErrorCode errorCode) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }
}
