package com.wangning.counter.event;

import com.wangning.counter.service.UserCounterService;
import com.wangning.knowpost.event.KnowPostPublishedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 将已提交的知文事务同步到用户计数 SDS。
 */
@Component
@RequiredArgsConstructor
public class UserCounterEventListener {

    private final UserCounterService userCounterService;

    /**
     * 在知文发布事务提交后增加作者的已发布知文数。
     *
     * @param event 已提交的知文发布事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onKnowPostPublished(KnowPostPublishedEvent event) {
        if (userCounterService.isInitialized(event.creatorId())) {
            userCounterService.incrementPosts(event.creatorId(), 1);
        }
    }
}
