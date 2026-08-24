package com.wangning.knowpost.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.counter.service.CounterService;
import com.wangning.knowpost.domain.KnowPost;
import com.wangning.knowpost.domain.KnowPostDetailRow;
import com.wangning.knowpost.domain.SnowflakeIdGenerator;
import com.wangning.knowpost.mapper.KnowPostMapper;
import com.wangning.knowpost.service.impl.KnowPostServiceImpl;
import com.wangning.storage.ObjectStorageService;
import com.wangning.user.domain.User;
import com.wangning.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class KnowPostServiceImplTest {

    @Mock
    private KnowPostMapper knowPostMapper;

    @Mock
    private UserService userService;

    @Mock
    private ObjectProvider<ObjectStorageService> objectStorageServiceProvider;

    @Mock
    private ObjectStorageService objectStorageService;

    @Mock
    private CounterService counterService;

    private KnowPostService knowPostService;

    @BeforeEach
    void setUp() {
        knowPostService = new KnowPostServiceImpl(
                knowPostMapper,
                new SnowflakeIdGenerator(0, 0),
                new ObjectMapper(),
                userService,
                objectStorageServiceProvider,
                counterService
        );
        lenient().when(userService.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
    }

    @Test
    void shouldCreateDraftForExistingUser() {
        when(knowPostMapper.insertDraft(any(KnowPost.class))).thenReturn(1);

        long id = knowPostService.createDraft(1L);

        ArgumentCaptor<KnowPost> captor = ArgumentCaptor.forClass(KnowPost.class);
        verify(knowPostMapper).insertDraft(captor.capture());
        KnowPost draft = captor.getValue();
        assertThat(id).isPositive();
        assertThat(draft.getId()).isEqualTo(id);
        assertThat(draft.getCreatorId()).isEqualTo(1L);
        assertThat(draft.getStatus()).isEqualTo("draft");
        assertThat(draft.getType()).isEqualTo("image_text");
        assertThat(draft.getVisible()).isEqualTo("public");
        assertThat(draft.getIsTop()).isFalse();
        assertThat(draft.getCreateTime()).isNotNull();
        assertThat(draft.getUpdateTime()).isEqualTo(draft.getCreateTime());
    }

    @Test
    void shouldRejectCreatingDraftForUnknownUser() {
        when(userService.findById(99L)).thenReturn(Optional.empty());

        assertErrorCode(() -> knowPostService.createDraft(99L), ErrorCode.RESOURCE_NOT_FOUND);

        verify(knowPostMapper, never()).insertDraft(any());
    }

    @Test
    void shouldConfirmContentUsingStablePublicUrl() {
        when(objectStorageServiceProvider.getIfAvailable()).thenReturn(objectStorageService);
        when(objectStorageService.publicUrl("posts/100/content.md"))
                .thenReturn("https://static.example.com/posts/100/content.md");
        when(knowPostMapper.updateContent(any(KnowPost.class))).thenReturn(1);

        knowPostService.confirmContent(
                1L,
                100L,
                "posts/100/content.md",
                "etag-100",
                512L,
                "a".repeat(64)
        );

        ArgumentCaptor<KnowPost> captor = ArgumentCaptor.forClass(KnowPost.class);
        verify(knowPostMapper).updateContent(captor.capture());
        KnowPost content = captor.getValue();
        assertThat(content.getContentUrl()).isEqualTo("https://static.example.com/posts/100/content.md");
        assertThat(content.getContentObjectKey()).isEqualTo("posts/100/content.md");
        assertThat(content.getContentEtag()).isEqualTo("etag-100");
        assertThat(content.getContentSize()).isEqualTo(512L);
        assertThat(content.getContentSha256()).isEqualTo("a".repeat(64));
    }

    @Test
    void shouldRejectContentConfirmationWhenStorageIsDisabled() {
        when(objectStorageServiceProvider.getIfAvailable()).thenReturn(null);

        assertErrorCode(
                () -> knowPostService.confirmContent(1L, 100L, "posts/100/content.md", "etag", 1L, "hash"),
                ErrorCode.STORAGE_CONFIGURATION_ERROR
        );
        verify(knowPostMapper, never()).updateContent(any());
    }

    @Test
    void shouldStoreSubmittedTagsAndImageUrlsAsJson() throws Exception {
        when(knowPostMapper.updateMetadata(any(KnowPost.class))).thenReturn(1);

        knowPostService.updateMetadata(
                1L,
                100L,
                "知文标题",
                null,
                List.of("Java", "MyBatis"),
                List.of("https://oss.example.com/posts/100/images/a.png"),
                "unlisted",
                false,
                "知文摘要"
        );

        ArgumentCaptor<KnowPost> captor = ArgumentCaptor.forClass(KnowPost.class);
        verify(knowPostMapper).updateMetadata(captor.capture());
        KnowPost metadata = captor.getValue();
        ObjectMapper objectMapper = new ObjectMapper();
        assertThat(objectMapper.readTree(metadata.getTags()))
                .isEqualTo(objectMapper.readTree("[\"Java\",\"MyBatis\"]"));
        assertThat(objectMapper.readTree(metadata.getImgUrls()))
                .isEqualTo(objectMapper.readTree("[\"https://oss.example.com/posts/100/images/a.png\"]"));
        assertThat(metadata.getVisible()).isEqualTo("unlisted");
        assertThat(metadata.getType()).isEqualTo("image_text");
    }

    @Test
    void shouldRejectUnknownVisibility() {
        assertErrorCode(
                () -> knowPostService.updateMetadata(
                        1L, 100L, null, null, null, null, "unknown", null, null
                ),
                ErrorCode.BAD_REQUEST
        );
        verify(knowPostMapper, never()).updateMetadata(any());
    }

    @Test
    void shouldPublishOwnedPostAndTranslateMissingPost() {
        when(knowPostMapper.publish(100L, 1L)).thenReturn(1, 0);

        knowPostService.publish(1L, 100L);
        assertErrorCode(() -> knowPostService.publish(1L, 100L), ErrorCode.BAD_REQUEST);

        verify(knowPostMapper, times(2)).publish(100L, 1L);
    }

    @Test
    void shouldUpdateTopVisibilityAndDeleteOwnedPost() {
        when(knowPostMapper.updateTop(100L, 1L, true)).thenReturn(1);
        when(knowPostMapper.updateVisibility(100L, 1L, "followers")).thenReturn(1);
        when(knowPostMapper.softDelete(100L, 1L)).thenReturn(1);

        knowPostService.updateTop(1L, 100L, true);
        knowPostService.updateVisibility(1L, 100L, "followers");
        knowPostService.delete(1L, 100L);

        verify(knowPostMapper).updateTop(100L, 1L, true);
        verify(knowPostMapper).updateVisibility(100L, 1L, "followers");
        verify(knowPostMapper).softDelete(100L, 1L);
    }

    @Test
    void shouldRejectUnknownVisibilityOnDedicatedEndpoint() {
        assertErrorCode(() -> knowPostService.updateVisibility(1L, 100L, "unknown"), ErrorCode.BAD_REQUEST);

        verify(knowPostMapper, never()).updateVisibility(anyLong(), anyLong(), anyString());
    }

    @Test
    void shouldReturnPublicDetailAndRestrictNonPublicDetailToOwner() {
        KnowPostDetailRow row = detailRow("published", "public");
        when(knowPostMapper.findDetailById(100L)).thenReturn(row);
        when(counterService.getCounts("knowpost", "100", List.of("like", "fav")))
                .thenReturn(Map.of("like", 8L, "fav", 3L));
        when(counterService.isLiked("knowpost", "100", 1L)).thenReturn(true);
        when(counterService.isFaved("knowpost", "100", 1L)).thenReturn(true);

        var publicDetail = knowPostService.getDetail(100L, null);

        assertThat(publicDetail.id()).isEqualTo("100");
        assertThat(publicDetail.images()).containsExactly("https://static.example.com/posts/100/images/a.png");
        assertThat(publicDetail.tags()).containsExactly("Java", "MyBatis");
        assertThat(publicDetail.likeCount()).isEqualTo(8L);
        assertThat(publicDetail.liked()).isFalse();

        row.setVisible("private");
        assertErrorCode(() -> knowPostService.getDetail(100L, 2L), ErrorCode.BAD_REQUEST);
        var ownerDetail = knowPostService.getDetail(100L, 1L);
        assertThat(ownerDetail.visible()).isEqualTo("private");
        assertThat(ownerDetail.liked()).isTrue();
        assertThat(ownerDetail.faved()).isTrue();
    }

    @Test
    void shouldRejectDeletedOrMissingDetail() {
        when(knowPostMapper.findDetailById(100L)).thenReturn(detailRow("deleted", "public"));

        assertErrorCode(() -> knowPostService.getDetail(100L, 1L), ErrorCode.BAD_REQUEST);

        when(knowPostMapper.findDetailById(101L)).thenReturn(null);
        assertErrorCode(() -> knowPostService.getDetail(101L, null), ErrorCode.BAD_REQUEST);
    }

    private KnowPostDetailRow detailRow(String status, String visible) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(100L);
        row.setCreatorId(1L);
        row.setTitle("知文标题");
        row.setDescription("知文摘要");
        row.setTags("[\"Java\",\"MyBatis\"]");
        row.setImgUrls("[\"https://static.example.com/posts/100/images/a.png\"]");
        row.setContentUrl("https://static.example.com/posts/100/content.md");
        row.setAuthorAvatar("https://static.example.com/avatars/1.png");
        row.setAuthorNickname("作者");
        row.setAuthorTagJson("[\"Java\"]");
        row.setIsTop(false);
        row.setVisible(visible);
        row.setType("image_text");
        row.setStatus(status);
        return row;
    }

    private void assertErrorCode(Runnable runnable, ErrorCode errorCode) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }
}
