package com.wangning.search.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.search.event.KnowPostIndexEvent;
import com.wangning.search.processor.SearchIndexEventProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SearchOutboxConsumerTest {

    @Mock
    private SearchIndexEventProcessor processor;

    @Mock
    private Acknowledgment acknowledgment;

    @Test
    void shouldProcessSearchEventAndIgnoreEventsForOtherModules() {
        SearchOutboxConsumer consumer = new SearchOutboxConsumer(new ObjectMapper(), processor);

        consumer.onMessage("""
                {
                  "table":"outbox",
                  "type":"INSERT",
                  "data":[
                    {"id":"101","payload":"{\\"type\\":\\"KnowPostIndexRequested\\",\\"knowPostId\\":99}"},
                    {"id":"102","payload":"{\\"type\\":\\"FollowCreated\\",\\"fromUserId\\":1}"}
                  ]
                }
                """, acknowledgment);

        ArgumentCaptor<KnowPostIndexEvent> eventCaptor = ArgumentCaptor.forClass(KnowPostIndexEvent.class);
        verify(processor).process(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(new KnowPostIndexEvent(KnowPostIndexEvent.TYPE, 99L));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void shouldAcknowledgeMalformedPayloadWithoutCallingProcessor() {
        SearchOutboxConsumer consumer = new SearchOutboxConsumer(new ObjectMapper(), processor);

        consumer.onMessage("""
                {"table":"outbox","type":"INSERT","data":[{"id":"101","payload":"not-json"}]}
                """, acknowledgment);

        verify(processor, never()).process(org.mockito.ArgumentMatchers.any());
        verify(acknowledgment).acknowledge();
    }
}
