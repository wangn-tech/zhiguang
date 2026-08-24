package com.wangning.knowpost.mapper;

import com.wangning.knowpost.domain.KnowPost;
import com.wangning.knowpost.domain.KnowPostDetailRow;
import com.wangning.knowpost.domain.KnowPostFeedRow;
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
class KnowPostMapperTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("zhiguang_test")
            .withUsername("zhiguang")
            .withPassword("zhiguang_test")
            .withInitScript("schema/knowpost-schema.sql");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    private KnowPostMapper knowPostMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long authorId;
    private long otherAuthorId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM know_posts");
        jdbcTemplate.update("DELETE FROM users");
        authorId = insertUser("作者");
        otherAuthorId = insertUser("其他作者");
    }

    @Test
    void shouldInsertDraftAndUpdateContentAndMetadata() {
        KnowPost draft = draft(8_001L, authorId);

        assertThat(knowPostMapper.insertDraft(draft)).isEqualTo(1);
        assertThat(knowPostMapper.updateContent(KnowPost.builder()
                .id(draft.getId())
                .creatorId(authorId)
                .contentUrl("https://static.example.com/posts/8001/content.md")
                .contentObjectKey("posts/8001/content.md")
                .contentEtag("etag-1")
                .contentSize(128L)
                .contentSha256("a".repeat(64))
                .updateTime(Instant.parse("2026-08-24T10:01:00Z"))
                .build())).isEqualTo(1);
        assertThat(knowPostMapper.updateMetadata(KnowPost.builder()
                .id(draft.getId())
                .creatorId(authorId)
                .title("MyBatis 入门")
                .description("知文摘要")
                .tags("[\"Java\",\"MyBatis\"]")
                .imgUrls("[\"https://static.example.com/posts/8001/images/a.png\"]")
                .visible("private")
                .isTop(true)
                .type("image_text")
                .updateTime(Instant.parse("2026-08-24T10:02:00Z"))
                .build())).isEqualTo(1);

        KnowPost stored = knowPostMapper.findById(draft.getId());
        assertThat(stored.getStatus()).isEqualTo("draft");
        assertThat(stored.getContentObjectKey()).isEqualTo("posts/8001/content.md");
        assertThat(stored.getContentSize()).isEqualTo(128L);
        assertThat(stored.getTitle()).isEqualTo("MyBatis 入门");
        assertThat(stored.getTags()).isEqualTo("[\"Java\", \"MyBatis\"]");
        assertThat(stored.getImgUrls()).isEqualTo("[\"https://static.example.com/posts/8001/images/a.png\"]");
        assertThat(stored.getVisible()).isEqualTo("private");
        assertThat(stored.getIsTop()).isTrue();
    }

    @Test
    void shouldReturnOnlyPublicPostsInFeedAndOrderMineByTop() {
        insertPublishedPost(8_011L, authorId, "公开内容", "public", false, "2026-08-24 10:00:00");
        insertPublishedPost(8_012L, authorId, "私密内容", "private", true, "2026-08-24 11:00:00");
        insertPublishedPost(8_013L, otherAuthorId, "其他公开内容", "public", false, "2026-08-24 12:00:00");
        insertPublishedPost(8_014L, authorId, "已删除内容", "public", false, "2026-08-24 13:00:00");
        jdbcTemplate.update("UPDATE know_posts SET status = 'deleted' WHERE id = ?", 8_014L);

        List<KnowPostFeedRow> publicFeed = knowPostMapper.listFeedPublic(20, 0);
        List<KnowPostFeedRow> mine = knowPostMapper.listMyPublished(authorId, 20, 0);

        assertThat(publicFeed).extracting(KnowPostFeedRow::getId)
                .containsExactly(8_013L, 8_011L);
        assertThat(mine).extracting(KnowPostFeedRow::getId)
                .containsExactly(8_012L, 8_011L);
        assertThat(publicFeed.getFirst().getAuthorNickname()).isEqualTo("其他作者");
    }

    @Test
    void shouldFindDetailAndOnlyUpdateOwnedPost() {
        insertPublishedPost(8_021L, authorId, "详情内容", "unlisted", false, "2026-08-24 10:00:00");

        KnowPostDetailRow detail = knowPostMapper.findDetailById(8_021L);

        assertThat(detail.getCreatorId()).isEqualTo(authorId);
        assertThat(detail.getVisible()).isEqualTo("unlisted");
        assertThat(detail.getContentUrl()).isEqualTo("https://static.example.com/posts/8021/content.md");
        assertThat(knowPostMapper.updateVisibility(8_021L, otherAuthorId, "public")).isZero();
        assertThat(knowPostMapper.softDelete(8_021L, authorId)).isEqualTo(1);
        assertThat(knowPostMapper.findById(8_021L).getStatus()).isEqualTo("deleted");
    }

    @Test
    void shouldSearchPublicFallbackByKeywordTagsAndKeysetCursor() {
        insertPublishedPost(8_031L, authorId, "Java 置顶", "public", true, "2026-08-24 10:00:00");
        insertPublishedPost(8_032L, otherAuthorId, "Java 普通", "public", false, "2026-08-24 11:00:00");
        insertPublishedPost(8_033L, authorId, "Python 内容", "public", false, "2026-08-24 12:00:00");
        insertPublishedPost(8_034L, authorId, "Java 私密", "private", false, "2026-08-24 13:00:00");

        List<KnowPostFeedRow> firstPage = knowPostMapper.searchPublicFallback(
                "Java", "[\"Java\"]", null, null, null, 20
        );
        List<KnowPostFeedRow> afterTop = knowPostMapper.searchPublicFallback(
                "Java",
                "[\"Java\"]",
                firstPage.getFirst().getIsTop(),
                firstPage.getFirst().getPublishTime(),
                firstPage.getFirst().getId(),
                20
        );

        assertThat(firstPage).extracting(KnowPostFeedRow::getId).containsExactly(8_031L, 8_032L);
        assertThat(afterTop).extracting(KnowPostFeedRow::getId).containsExactly(8_032L);
        assertThat(knowPostMapper.listPublicTitleSuggestionsFallback("Java", 10))
                .containsExactly("Java 普通", "Java 置顶");
    }

    private long insertUser(String nickname) {
        jdbcTemplate.update("""
                INSERT INTO users (nickname, avatar, tags_json)
                VALUES (?, ?, CAST(? AS JSON))
                """, nickname, "https://static.example.com/avatar.png", "[\"Java\"]");
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private KnowPost draft(long id, long creatorId) {
        Instant now = Instant.parse("2026-08-24T10:00:00Z");
        return KnowPost.builder()
                .id(id)
                .creatorId(creatorId)
                .status("draft")
                .type("image_text")
                .visible("public")
                .isTop(false)
                .createTime(now)
                .updateTime(now)
                .build();
    }

    private void insertPublishedPost(
            long id,
            long creatorId,
            String title,
            String visible,
            boolean isTop,
            String publishTime
    ) {
        jdbcTemplate.update("""
                INSERT INTO know_posts (
                    id, creator_id, title, content_url, tags, img_urls, status, type, visible, is_top,
                    create_time, update_time, publish_time
                ) VALUES (?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), 'published', 'image_text', ?, ?, NOW(), NOW(), ?)
                """,
                id,
                creatorId,
                title,
                "https://static.example.com/posts/%d/content.md".formatted(id),
                "[\"Java\"]",
                "[\"https://static.example.com/posts/%d/images/a.png\"]".formatted(id),
                visible,
                isTop,
                publishTime
        );
    }
}
