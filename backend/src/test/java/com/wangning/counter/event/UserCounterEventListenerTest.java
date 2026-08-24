package com.wangning.counter.event;

import com.wangning.counter.service.UserCounterService;
import com.wangning.knowpost.event.KnowPostPublishedEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UserCounterEventListenerTest {

    @Test
    void shouldIncreasePostCountAfterPublishCommit() {
        UserCounterService userCounterService = mock(UserCounterService.class);
        UserCounterEventListener listener = new UserCounterEventListener(userCounterService);

        listener.onKnowPostPublished(new KnowPostPublishedEvent(1L));

        verify(userCounterService).incrementPosts(1L, 1);
    }
}
