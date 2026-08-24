package com.wangning.relation.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.relation.event.RelationEvent;
import com.wangning.relation.processor.RelationEventProcessor;
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
class CanalOutboxConsumerTest {

    @Mock
    private RelationEventProcessor processor;

    @Mock
    private Acknowledgment acknowledgment;

    @Test
    void shouldProcessEveryValidOutboxRowBeforeAcknowledging() {
        CanalOutboxConsumer consumer = new CanalOutboxConsumer(new ObjectMapper(), processor);

        consumer.onMessage("""
                {
                  "table":"outbox",
                  "type":"INSERT",
                  "data":[
                    {"id":"101","payload":"{\\"type\\":\\"FollowCreated\\",\\"fromUserId\\":1,\\"toUserId\\":2,\\"relationId\\":99}"}
                  ]
                }
                """, acknowledgment);

        ArgumentCaptor<RelationEvent> eventCaptor = ArgumentCaptor.forClass(RelationEvent.class);
        verify(processor).process(org.mockito.ArgumentMatchers.eq(101L), eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(new RelationEvent("FollowCreated", 1L, 2L, 99L));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void shouldAcknowledgeInvalidPayloadWithoutCallingProcessor() {
        CanalOutboxConsumer consumer = new CanalOutboxConsumer(new ObjectMapper(), processor);

        consumer.onMessage("""
                {"table":"outbox","type":"INSERT","data":[{"id":"101","payload":"not-json"}]}
                """, acknowledgment);

        verify(processor, never()).process(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
        verify(acknowledgment).acknowledge();
    }
}
