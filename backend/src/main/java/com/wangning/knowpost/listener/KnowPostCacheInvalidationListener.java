package com.wangning.knowpost.listener;

import com.wangning.cache.service.KnowPostDetailCacheService;
import com.wangning.knowpost.event.KnowPostChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 在知文事务提交后失效相关缓存。
 *
 * <p>当前阶段处理详情缓存；公共 Feed 和作者 Feed 缓存接入后会在同一监听器中增加对应失效逻辑。</p>
 */
@Component
@RequiredArgsConstructor
public class KnowPostCacheInvalidationListener {

    private final KnowPostDetailCacheService knowPostDetailCacheService;

    /**
     * 删除已提交变更对应的详情快照。
     *
     * @param event 知文变更事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onKnowPostChanged(KnowPostChangedEvent event) {
        knowPostDetailCacheService.invalidate(event.knowPostId());
    }
}
