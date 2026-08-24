package com.wangning.counter.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.counter.config.CounterEventProperties;
import com.wangning.counter.schema.CounterKeys;
import com.wangning.counter.schema.CounterMetric;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 互动计数事件的 Kafka 聚合消费者。
 *
 * <p>事件先原子写入 Redis 聚合桶并建立索引，再确认 Kafka 位点。定时任务以 Lua 原子地将整桶
 * 增量折叠到实体 SDS、清空桶并移除索引，过程中不会丢失并发进入的新增量。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "counter.events", name = "enabled", havingValue = "true")
public class CounterAggregationConsumer {

    private static final int SDS_FIELD_SIZE = 4;
    private static final int SDS_FIELD_COUNT = 5;
    private static final RedisScript<Long> ADD_TO_BUCKET_SCRIPT = RedisScript.of("""
            if not redis.call('SET', KEYS[3], '1', 'NX', 'PX', ARGV[3]) then
                return 0
            end
            redis.call('HINCRBY', KEYS[1], ARGV[1], ARGV[2])
            redis.call('SADD', KEYS[2], KEYS[1])
            return 1
            """, Long.class);
    private static final RedisScript<Long> FOLD_BUCKET_SCRIPT = RedisScript.of("""
            local bucket = KEYS[1]
            local counter = KEYS[2]
            local indexKey = KEYS[3]
            local fieldSize = tonumber(ARGV[1])
            local fieldCount = tonumber(ARGV[2])
            local values = redis.call('HGETALL', bucket)
            if #values == 0 then
                redis.call('SREM', indexKey, bucket)
                return 0
            end

            local function read32be(source, offset)
                local b1, b2, b3, b4 = string.byte(source, offset + 1, offset + 4)
                return ((b1 or 0) * 16777216) + ((b2 or 0) * 65536) + ((b3 or 0) * 256) + (b4 or 0)
            end

            local function write32be(number)
                local bytes = {}
                for index = 4, 1, -1 do
                    bytes[index] = number % 256
                    number = math.floor(number / 256)
                end
                return string.char(unpack(bytes))
            end

            local value = redis.call('GET', counter)
            if not value then
                value = string.rep(string.char(0), fieldSize * fieldCount)
            end
            for position = 1, #values, 2 do
                local field = tonumber(values[position])
                local delta = tonumber(values[position + 1])
                if field and delta and field >= 0 and field < fieldCount then
                    local offset = field * fieldSize
                    local nextValue = read32be(value, offset) + delta
                    if nextValue < 0 then
                        nextValue = 0
                    end
                    value = string.sub(value, 1, offset)
                            .. write32be(nextValue)
                            .. string.sub(value, offset + fieldSize + 1)
                end
            end
            redis.call('SET', counter, value)
            redis.call('DEL', bucket)
            redis.call('SREM', indexKey, bucket)
            return #values / 2
            """, Long.class);

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final CounterEventProperties properties;

    /**
     * 消费一条 Kafka 互动计数事件。
     *
     * @param message 计数事件 JSON
     * @param acknowledgment Kafka 手动确认对象
     */
    @KafkaListener(topics = "${counter.events.topic}", groupId = "${counter.events.consumer-group}")
    public void onMessage(String message, Acknowledgment acknowledgment) {
        try {
            CounterEvent event = objectMapper.readValue(message, CounterEvent.class);
            aggregate(event);
            acknowledgment.acknowledge();
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            // 脏消息无法通过重试恢复，确认位点以避免阻塞同一分区。
            log.warn("Skip invalid counter event: {}", exception.getMessage());
            acknowledgment.acknowledge();
        }
    }

    /**
     * 将已校验的事件原子写入 Redis 聚合桶。
     *
     * @param event 计数事件
     * @throws IllegalArgumentException 事件字段不符合当前 Schema 时抛出
     */
    public void aggregate(CounterEvent event) {
        validate(event);
        redisTemplate.execute(
                ADD_TO_BUCKET_SCRIPT,
                List.of(
                        CounterKeys.aggregationKey(event.getEntityType(), event.getEntityId()),
                        CounterKeys.aggregationIndexKey(),
                        CounterKeys.eventDedupKey(event.getEventId())
                ),
                String.valueOf(event.getIndex()),
                String.valueOf(event.getDelta()),
                String.valueOf(properties.getDedupTtl().toMillis())
        );
    }

    /**
     * 定期将索引中的聚合桶折叠到实体 SDS。
     */
    @Scheduled(fixedDelayString = "${counter.events.flush-interval:PT1S}")
    public void flush() {
        ScanOptions options = ScanOptions.scanOptions().count(properties.getFlushBatchSize()).build();
        try (Cursor<String> cursor = redisTemplate.opsForSet().scan(CounterKeys.aggregationIndexKey(), options)) {
            int handled = 0;
            while (cursor.hasNext() && handled++ < properties.getFlushBatchSize()) {
                flushBucket(cursor.next());
            }
        }
    }

    private void flushBucket(String aggregationKey) {
        String[] parts = aggregationKey.split(":", 4);
        if (parts.length != 4 || !"agg".equals(parts[0]) || !"v1".equals(parts[1])) {
            redisTemplate.opsForSet().remove(CounterKeys.aggregationIndexKey(), aggregationKey);
            log.warn("Removed malformed counter aggregation key: {}", aggregationKey);
            return;
        }
        redisTemplate.execute(
                FOLD_BUCKET_SCRIPT,
                List.of(
                        aggregationKey,
                        CounterKeys.sdsKey(parts[2], parts[3]),
                        CounterKeys.aggregationIndexKey()
                ),
                String.valueOf(SDS_FIELD_SIZE),
                String.valueOf(SDS_FIELD_COUNT)
        );
    }

    private void validate(CounterEvent event) {
        if (event == null || event.getEventId() == null || event.getEventId().isBlank()
                || !"knowpost".equals(event.getEntityType())
                || event.getEntityId() == null || !event.getEntityId().matches("[1-9]\\d*")
                || event.getUserId() <= 0 || (event.getDelta() != 1 && event.getDelta() != -1)) {
            throw new IllegalArgumentException("无效的计数事件");
        }
        boolean metricMatchesSchema = (CounterMetric.LIKE.value().equals(event.getMetric())
                && event.getIndex() == CounterMetric.LIKE.index())
                || (CounterMetric.FAV.value().equals(event.getMetric())
                && event.getIndex() == CounterMetric.FAV.index());
        if (!metricMatchesSchema) {
            throw new IllegalArgumentException("计数事件指标与 Schema 不匹配");
        }
    }
}
