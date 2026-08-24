package com.wangning.search.processor;

import com.wangning.search.event.KnowPostIndexEvent;
import com.wangning.search.index.SearchIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Objects;

/**
 * 处理知文搜索索引 Outbox 事件。
 *
 * <p>Elasticsearch 以知文 ID 覆盖写入文档，处理本身是幂等的；每次处理均读取 MySQL 当前状态，
 * 从而避免旧消息覆盖新状态。</p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "search", name = "enabled", havingValue = "true")
public class SearchIndexEventProcessor {

    private final SearchIndexService searchIndexService;

    /**
     * 处理一条知文索引请求。
     *
     * @param event 已提交的 Outbox 事件
     * @throws IllegalArgumentException 事件类型或知文 ID 不合法时抛出
     * @throws IllegalStateException Elasticsearch 写入失败时抛出，以触发 Kafka 重投
     */
    public void process(KnowPostIndexEvent event) {
        if (event == null || event.knowPostId() <= 0 || !Objects.equals(KnowPostIndexEvent.TYPE, event.type())) {
            throw new IllegalArgumentException("无效的知文搜索索引事件");
        }
        try {
            searchIndexService.upsertKnowPost(event.knowPostId());
        } catch (IOException exception) {
            throw new IllegalStateException("写入知文搜索索引失败", exception);
        }
    }
}
