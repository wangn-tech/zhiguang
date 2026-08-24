package com.wangning.search.processor;

import com.wangning.search.event.KnowPostIndexEvent;
import com.wangning.search.index.SearchIndexService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SearchIndexEventProcessorTest {

    @Mock
    private SearchIndexService searchIndexService;

    @Test
    void shouldIndexKnowPostForValidEvent() throws Exception {
        SearchIndexEventProcessor processor = new SearchIndexEventProcessor(searchIndexService);

        processor.process(new KnowPostIndexEvent(KnowPostIndexEvent.TYPE, 100L));

        verify(searchIndexService).upsertKnowPost(100L);
    }

    @Test
    void shouldRejectInvalidEvent() {
        SearchIndexEventProcessor processor = new SearchIndexEventProcessor(searchIndexService);

        assertThatThrownBy(() -> processor.process(new KnowPostIndexEvent("unknown", 100L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldExposeElasticsearchFailureForKafkaRetry() throws Exception {
        SearchIndexEventProcessor processor = new SearchIndexEventProcessor(searchIndexService);
        doThrow(new IOException("offline")).when(searchIndexService).upsertKnowPost(100L);

        assertThatThrownBy(() -> processor.process(new KnowPostIndexEvent(KnowPostIndexEvent.TYPE, 100L)))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(IOException.class);
    }
}
