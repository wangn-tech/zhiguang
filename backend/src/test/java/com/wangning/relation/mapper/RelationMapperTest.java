package com.wangning.relation.mapper;

import com.wangning.relation.domain.RelationListItem;
import com.wangning.relation.domain.UserRelation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RelationMapperTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("zhiguang_test")
            .withUsername("zhiguang")
            .withPassword("zhiguang_test")
            .withInitScript("schema/relation-schema.sql");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    private RelationMapper relationMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long aliceId;
    private long bobId;
    private long carolId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM follower");
        jdbcTemplate.update("DELETE FROM following");
        jdbcTemplate.update("DELETE FROM users");
        aliceId = insertUser("Alice");
        bobId = insertUser("Bob");
        carolId = insertUser("Carol");
    }

    @Test
    void shouldCreateRestoreAndDeactivateForwardRelation() {
        Instant createdAt = Instant.parse("2026-08-24T10:00:00Z");
        Instant restoredAt = Instant.parse("2026-08-24T11:00:00Z");

        assertThat(relationMapper.upsertFollowing(relation(9_001L, aliceId, bobId, createdAt))).isEqualTo(1);
        assertThat(relationMapper.existsFollowing(aliceId, bobId)).isTrue();
        assertThat(relationMapper.deactivateFollowing(aliceId, bobId, createdAt.plusSeconds(60))).isEqualTo(1);
        assertThat(relationMapper.deactivateFollowing(aliceId, bobId, createdAt.plusSeconds(61))).isZero();
        assertThat(relationMapper.existsFollowing(aliceId, bobId)).isFalse();

        assertThat(relationMapper.upsertFollowing(relation(9_002L, aliceId, bobId, restoredAt))).isGreaterThan(0);

        UserRelation stored = relationMapper.findFollowing(aliceId, bobId);
        assertThat(stored.getId()).isEqualTo(9_001L);
        assertThat(stored.getActive()).isTrue();
        assertThat(stored.getCreatedAt()).isEqualTo(restoredAt);
        assertThat(stored.getUpdatedAt()).isEqualTo(restoredAt);
    }

    @Test
    void shouldMaintainReverseRelationIndependently() {
        Instant now = Instant.parse("2026-08-24T10:00:00Z");

        assertThat(relationMapper.upsertFollower(relation(9_011L, aliceId, bobId, now))).isEqualTo(1);
        assertThat(relationMapper.findFollower(bobId, aliceId))
                .extracting(UserRelation::getFromUserId, UserRelation::getToUserId, UserRelation::getActive)
                .containsExactly(aliceId, bobId, true);
        assertThat(relationMapper.deactivateFollower(bobId, aliceId, now.plusSeconds(1))).isEqualTo(1);
        assertThat(relationMapper.findFollower(bobId, aliceId).getActive()).isFalse();
    }

    @Test
    void shouldListActiveRelationsByOffsetAndCursorAndCountThem() {
        Instant ten = Instant.parse("2026-08-24T10:00:00Z");
        Instant eleven = Instant.parse("2026-08-24T11:00:00Z");
        Instant noon = Instant.parse("2026-08-24T12:00:00Z");
        relationMapper.upsertFollowing(relation(9_021L, aliceId, bobId, ten));
        relationMapper.upsertFollowing(relation(9_022L, aliceId, carolId, eleven));
        relationMapper.upsertFollowing(relation(9_023L, bobId, aliceId, noon));
        relationMapper.upsertFollower(relation(9_024L, bobId, aliceId, ten));
        relationMapper.upsertFollower(relation(9_025L, carolId, aliceId, eleven));
        relationMapper.deactivateFollowing(aliceId, bobId, noon.plusSeconds(1));

        List<RelationListItem> followings = relationMapper.listFollowings(aliceId, 20, 0);
        List<RelationListItem> followers = relationMapper.listFollowers(aliceId, 20, 0);
        List<RelationListItem> cursorPage = relationMapper.listFollowersBefore(aliceId, noon, 20);

        assertThat(followings).extracting(RelationListItem::getUserId).containsExactly(carolId);
        assertThat(followers).extracting(RelationListItem::getUserId).containsExactly(carolId, bobId);
        assertThat(cursorPage).extracting(RelationListItem::getUserId).containsExactly(carolId, bobId);
        assertThat(relationMapper.listFollowingsBefore(aliceId, noon, 20))
                .extracting(RelationListItem::getUserId)
                .containsExactly(carolId);
        assertThat(relationMapper.countFollowings(aliceId)).isEqualTo(1);
        assertThat(relationMapper.countFollowers(aliceId)).isEqualTo(2);
    }

    private long insertUser(String nickname) {
        jdbcTemplate.update("INSERT INTO users (nickname) VALUES (?)", nickname);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private UserRelation relation(long id, long fromUserId, long toUserId, Instant timestamp) {
        return UserRelation.builder()
                .id(id)
                .fromUserId(fromUserId)
                .toUserId(toUserId)
                .createdAt(timestamp)
                .updatedAt(timestamp)
                .build();
    }
}
