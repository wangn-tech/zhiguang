package com.wangning.search.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.knowpost.domain.KnowPostDetailRow;
import com.wangning.search.config.SearchProperties;
import com.wangning.storage.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowPostSearchDocumentFactoryTest {

    @Test
    void shouldProjectPublicKnowPostWithObjectStorageBody() {
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        when(storageService.download("posts/100/content/content.md"))
                .thenReturn(new ByteArrayInputStream("完整正文".getBytes(StandardCharsets.UTF_8)));
        KnowPostSearchDocumentFactory factory = factory(storageService);

        KnowPostSearchDocument document = factory.create(publicRow());

        assertThat(document.id()).isEqualTo(100L);
        assertThat(document.body()).isEqualTo("完整正文");
        assertThat(document.tags()).containsExactly("Java", "MyBatis");
        assertThat(document.imgUrls()).containsExactly("https://static.example.com/posts/100/a.png");
        assertThat(document.status()).isEqualTo("published");
        assertThat(document.titleSuggest()).isEqualTo("知文标题");
    }

    @Test
    void shouldFallBackToDescriptionWhenStorageIsUnavailable() {
        KnowPostSearchDocumentFactory factory = factory(null);

        KnowPostSearchDocument document = factory.create(publicRow());

        assertThat(document.body()).isEqualTo("知文摘要");
    }

    @Test
    void shouldRejectNonPublicOrUnpublishedKnowPost() {
        KnowPostSearchDocumentFactory factory = factory(null);
        KnowPostDetailRow row = publicRow();
        row.setVisible("private");

        assertThat(factory.isSearchable(row)).isFalse();
        assertThatThrownBy(() -> factory.create(row)).isInstanceOf(IllegalArgumentException.class);
    }

    @SuppressWarnings("unchecked")
    private KnowPostSearchDocumentFactory factory(ObjectStorageService storageService) {
        ObjectProvider<ObjectStorageService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(storageService);
        return new KnowPostSearchDocumentFactory(new ObjectMapper(), provider, new SearchProperties());
    }

    private KnowPostDetailRow publicRow() {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(100L);
        row.setCreatorId(7L);
        row.setTitle("知文标题");
        row.setDescription("知文摘要");
        row.setContentObjectKey("posts/100/content/content.md");
        row.setTags("[\"Java\",\"MyBatis\"]");
        row.setImgUrls("[\"https://static.example.com/posts/100/a.png\"]");
        row.setAuthorNickname("作者");
        row.setIsTop(false);
        row.setPublishTime(Instant.parse("2026-08-24T00:00:00Z"));
        row.setStatus("published");
        row.setVisible("public");
        return row;
    }
}
