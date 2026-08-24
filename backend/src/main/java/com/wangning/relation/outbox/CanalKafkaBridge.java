package com.wangning.relation.outbox;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wangning.relation.config.RelationEventProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;

/**
 * Canal 到 Kafka 的 Outbox 桥接器。
 *
 * <p>仅订阅 {@code outbox} 的 INSERT/UPDATE 行事件。Kafka 发送成功后才确认 Canal 位点，
 * 因此链路提供至少一次投递语义；下游通过 Outbox ID 幂等处理重复消息。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "relation.events", name = "enabled", havingValue = "true")
public class CanalKafkaBridge implements SmartLifecycle {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final RelationEventProperties properties;

    private volatile boolean running;
    private volatile CanalConnector connector;
    private Thread worker;

    /**
     * 异步启动 Canal 拉取循环。
     */
    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        worker = Thread.ofPlatform()
                .name("canal-kafka-bridge")
                .daemon(true)
                .start(this::runLoop);
    }

    /**
     * 停止拉取循环并断开 Canal 连接。
     */
    @Override
    public synchronized void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
        disconnect();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    /**
     * 容器销毁时确保释放 Canal 网络资源。
     */
    @PreDestroy
    void destroy() {
        stop();
    }

    private void runLoop() {
        try {
            RelationEventProperties.Canal canal = properties.getCanal();
            connector = CanalConnectors.newSingleConnector(
                    new InetSocketAddress(canal.getHost(), canal.getPort()),
                    canal.getDestination(),
                    canal.getUsername(),
                    canal.getPassword()
            );
            connector.connect();
            connector.subscribe(canal.getFilter());
            connector.rollback();
            log.info("Connected Canal bridge: destination={}, filter={}", canal.getDestination(), canal.getFilter());

            while (running) {
                forwardNextBatch(canal);
            }
        } catch (Exception exception) {
            if (running) {
                log.error("Canal bridge stopped unexpectedly", exception);
            }
        } finally {
            running = false;
            disconnect();
        }
    }

    private void forwardNextBatch(RelationEventProperties.Canal canal) throws Exception {
        Message message = connector.getWithoutAck(canal.getBatchSize());
        long batchId = message.getId();
        if (batchId == -1 || message.getEntries().isEmpty()) {
            Thread.sleep(canal.getPollInterval().toMillis());
            return;
        }

        try {
            for (CanalEntry.Entry entry : message.getEntries()) {
                forwardEntry(entry);
            }
            connector.ack(batchId);
        } catch (Exception exception) {
            connector.rollback(batchId);
            throw exception;
        }
    }

    private void forwardEntry(CanalEntry.Entry entry) throws Exception {
        if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA) {
            return;
        }
        CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
        if (rowChange.getEventType() != CanalEntry.EventType.INSERT
                && rowChange.getEventType() != CanalEntry.EventType.UPDATE) {
            return;
        }

        ArrayNode data = objectMapper.createArrayNode();
        for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
            ObjectNode row = objectMapper.createObjectNode();
            for (CanalEntry.Column column : rowData.getAfterColumnsList()) {
                if ("id".equalsIgnoreCase(column.getName()) || "payload".equalsIgnoreCase(column.getName())) {
                    row.put(column.getName().toLowerCase(), column.getValue());
                }
            }
            data.add(row);
        }

        ObjectNode message = objectMapper.createObjectNode();
        message.put("table", entry.getHeader().getTableName());
        message.put("type", rowChange.getEventType().name());
        message.set("data", data);
        kafkaTemplate.send(properties.getTopic(), objectMapper.writeValueAsString(message)).get();
    }

    private void disconnect() {
        CanalConnector currentConnector = connector;
        connector = null;
        if (currentConnector != null) {
            try {
                currentConnector.disconnect();
            } catch (Exception exception) {
                log.debug("Canal disconnect failed", exception);
            }
        }
    }
}
