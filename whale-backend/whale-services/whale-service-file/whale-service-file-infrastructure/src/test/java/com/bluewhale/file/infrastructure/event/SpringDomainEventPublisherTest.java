package com.bluewhale.file.infrastructure.event;

import com.bluewhale.file.domain.file.event.FileAssetDomainEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * {@link SpringDomainEventPublisher} 单元测试：事件透传与监听器异常降级。
 *
 * @author BlueWhale
 */
class SpringDomainEventPublisherTest {

    private final ApplicationEventPublisher springPublisher = mock(ApplicationEventPublisher.class);

    private final SpringDomainEventPublisher publisher = new SpringDomainEventPublisher(springPublisher);

    @Test
    @DisplayName("publish：事件原样透传给 Spring 事件发布器")
    void shouldDelegateToSpringPublisher() {
        FileAssetDomainEvent event = new FileAssetDomainEvent(7L, FileAssetDomainEvent.ChangeType.DELETED,
                1L, "bluewhale", "tenants/1/a.txt", 9L, 9L);

        publisher.publish(event);

        verify(springPublisher).publishEvent(event);
    }

    @Test
    @DisplayName("publish：监听器抛异常时只告警，不向调用方传播")
    void shouldSwallowListenerFailure() {
        doThrow(new IllegalStateException("listener boom")).when(springPublisher).publishEvent(any());
        FileAssetDomainEvent event = new FileAssetDomainEvent(7L, FileAssetDomainEvent.ChangeType.LOCKED,
                1L, "bluewhale", "tenants/1/a.txt", 9L, null);

        assertDoesNotThrow(() -> publisher.publish(event));
    }
}
