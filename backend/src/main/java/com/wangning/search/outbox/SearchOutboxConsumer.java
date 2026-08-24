package com.wangning.search.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.relation.outbox.CanalOutboxMessage;
import com.wangning.relation.outbox.OutboxMessageUtil;
import com.wangning.search.event.KnowPostIndexEvent;
import com.wangning.search.processor.SearchIndexEventProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 消费 Canal 转发的 Outbox 消息，并更新 Elasticsearch 知文索引。
 *
 * <p>使用独立消费者组订阅共享 Topic，因此不会影响关系模块处理相同的 Outbox 行。只确认已成功处理、
 * 或已明确判定为不可恢复脏数据的消息；Elasticsearch 故障会向 Kafka 抛出异常以触发重投。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "search", name = {"enabled", "events.enabled"}, havingValue = "true")
public class SearchOutboxConsumer {

    private final ObjectMapper objectMapper;
    private final SearchIndexEventProcessor processor;

    /**
     * 消费一批 Canal Outbox 行。
     *
     * @param message Canal JSON 消息
     * @param acknowledgment Kafka 手动确认对象
     */
    @KafkaListener(
            topics = "${relation.events.topic}",
            groupId = "${search.events.consumer-group}"
    )
    public void onMessage(String message, Acknowledgment acknowledgment) {
        List<CanalOutboxMessage> messages = OutboxMessageUtil.extractMessages(objectMapper, message);
        for (CanalOutboxMessage outboxMessage : messages) {
            processMessage(outboxMessage);
        }
        acknowledgment.acknowledge();
    }

    private void processMessage(CanalOutboxMessage outboxMessage) {
        try {
            JsonNode payload = objectMapper.readTree(outboxMessage.payload());
            if (!KnowPostIndexEvent.TYPE.equals(payload.path("type").asText())) {
                return;
            }
            processor.process(objectMapper.treeToValue(payload, KnowPostIndexEvent.class));
        } catch (IllegalArgumentException exception) {
            log.warn(
                    "Skip invalid search outbox event, outboxId={}, reason={}",
                    outboxMessage.outboxId(),
                    exception.getMessage()
            );
        } catch (JsonProcessingException exception) {
            log.warn(
                    "Skip malformed search outbox payload, outboxId={}, reason={}",
                    outboxMessage.outboxId(),
                    exception.getOriginalMessage()
            );
        }
    }
}
