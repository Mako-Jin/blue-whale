package com.bluewhale.auth.infrastructure.event;

import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.UserDomainEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.verify;

/**
 * {@link SpringDomainEventPublisher} 单元测试：领域事件原样透传给 Spring 事件发布器（AUTH-17 / T-DDD-11）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class SpringDomainEventPublisherTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private SpringDomainEventPublisher publisher;

    @Test
    @DisplayName("publish：将同一事件对象透传给 ApplicationEventPublisher")
    void shouldDelegateEventToSpringPublisher() {
        UserDomainEvent event = new UserDomainEvent(1L, ChangeType.UPDATED);

        publisher.publish(event);

        verify(applicationEventPublisher).publishEvent(event);
    }
}
