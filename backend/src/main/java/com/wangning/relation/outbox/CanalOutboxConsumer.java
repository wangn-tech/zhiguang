package com.wangning.relation.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.relation.event.RelationEvent;
import com.wangning.relation.processor.RelationEventProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Canal Outbox Kafka 消费者。
 *
 * <p>处理完成才手动确认 Kafka 位点。消息重投由处理器按 Outbox ID 去重。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "relation.events", name = "enabled", havingValue = "true")
public class CanalOutboxConsumer {

    private final ObjectMapper objectMapper;
    private final RelationEventProcessor processor;

    /**
     * 消费 Canal 转发的一批 Outbox 行。
     *
     * @param message Canal JSON 消息
     * @param acknowledgment Kafka 手动确认对象
     */
    @KafkaListener(
            topics = "${relation.events.topic}",
            groupId = "${relation.events.consumer-group}"
    )
    public void onMessage(String message, Acknowledgment acknowledgment) {
        List<CanalOutboxMessage> messages = OutboxMessageUtil.extractMessages(objectMapper, message);
        for (CanalOutboxMessage outboxMessage : messages) {
            try {
                RelationEvent event = objectMapper.readValue(outboxMessage.payload(), RelationEvent.class);
                processor.process(outboxMessage.outboxId(), event);
            } catch (IllegalArgumentException exception) {
                // 不可恢复的脏事件不能阻塞整个分区；业务处理异常会继续抛出以触发重投。
                log.warn(
                        "Skip invalid relation outbox event, outboxId={}, reason={}",
                        outboxMessage.outboxId(),
                        exception.getMessage()
                );
            } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                log.warn(
                        "Skip malformed relation outbox payload, outboxId={}, reason={}",
                        outboxMessage.outboxId(),
                        exception.getOriginalMessage()
                );
            }
        }
        acknowledgment.acknowledge();
    }
}
