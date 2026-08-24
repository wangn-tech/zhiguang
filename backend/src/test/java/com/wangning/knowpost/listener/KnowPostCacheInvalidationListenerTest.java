package com.wangning.knowpost.listener;

import com.wangning.cache.service.KnowPostDetailCacheService;
import com.wangning.cache.service.KnowPostFeedCacheService;
import com.wangning.knowpost.event.KnowPostChangedEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KnowPostCacheInvalidationListenerTest {

    @Test
    void shouldInvalidateDetailAfterKnowPostChanged() {
        KnowPostDetailCacheService detailCacheService = mock(KnowPostDetailCacheService.class);
        KnowPostFeedCacheService feedCacheService = mock(KnowPostFeedCacheService.class);
        KnowPostCacheInvalidationListener listener = new KnowPostCacheInvalidationListener(
                detailCacheService,
                feedCacheService
        );

        listener.onKnowPostChanged(new KnowPostChangedEvent(100L, 1L));

        verify(detailCacheService).invalidate(100L);
        verify(feedCacheService).invalidatePublic();
        verify(feedCacheService).invalidateMine(1L);
    }
}
