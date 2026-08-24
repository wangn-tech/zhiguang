package com.wangning.relation.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Canal Outbox 消息解析工具。
 */
public final class OutboxMessageUtil {

    private OutboxMessageUtil() {
    }

    /**
     * 从 Canal JSON 消息中提取 {@code outbox} 表的有效行。
     *
     * <p>仅接受 INSERT/UPDATE 事件。每一行必须带有数字 ID 和非空 payload；格式不合法的
     * 行会被跳过，防止单条脏数据阻塞整个 Kafka 分区。</p>
     *
     * @param objectMapper JSON 解析器
     * @param message Canal JSON 消息
     * @return 可消费的 Outbox 行
     */
    public static List<CanalOutboxMessage> extractMessages(ObjectMapper objectMapper, String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            if (!"outbox".equals(root.path("table").asText())) {
                return List.of();
            }
            String type = root.path("type").asText();
            if (!"INSERT".equals(type) && !"UPDATE".equals(type)) {
                return List.of();
            }
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return List.of();
            }

            List<CanalOutboxMessage> messages = new ArrayList<>();
            for (JsonNode row : data) {
                long outboxId = row.path("id").asLong(0L);
                String payload = row.path("payload").asText(null);
                if (outboxId > 0 && payload != null && !payload.isBlank()) {
                    messages.add(new CanalOutboxMessage(outboxId, payload));
                }
            }
            return List.copyOf(messages);
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
