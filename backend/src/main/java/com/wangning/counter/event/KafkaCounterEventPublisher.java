package com.wangning.counter.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.counter.config.CounterEventProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Kafka 计数事件发布实现。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "counter.events", name = "enabled", havingValue = "true")
public class KafkaCounterEventPublisher implements CounterEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final CounterEventProperties properties;

    /** {@inheritDoc} */
    @Override
    public void publish(CounterEvent event) {
        try {
            kafkaTemplate.send(
                    properties.getTopic(),
                    event.getEntityType() + ":" + event.getEntityId(),
                    objectMapper.writeValueAsString(event)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("计数事件序列化失败", exception);
        }
    }
}
