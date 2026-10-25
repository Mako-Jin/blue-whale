package com.bluewhale.pm.application.notify;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pm.infrastructure.client.ImNotifyClient;
import com.bluewhale.pm.infrastructure.config.PmNotifyProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link PmNotifyPublisher} 单元测试：开关零副作用、本人过滤、提交后投递与失败降级。
 *
 * @author BlueWhale
 */
class PmNotifyPublisherTest {

    private final PmNotifyProperties properties = new PmNotifyProperties();

    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final ImNotifyClient notifyClient = mock(ImNotifyClient.class);

    private final PmNotifyPublisher publisher = new PmNotifyPublisher(properties, eventPublisher, notifyClient);

    /**
     * 清理线程租户上下文。
     */
    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    /**
     * 构造指派事件。
     *
     * @param recipient 接收人
     * @param operator  操作人
     * @return 通知事件
     */
    private static PmNotifyEvent assignEvent(Long recipient, Long operator) {
        return PmNotifyEvent.builder()
                .projectId(3L)
                .workItemId(101L)
                .itemKey("PM-1")
                .itemTitle("修复登录缺陷")
                .kind(PmNotifyKind.ASSIGN)
                .operatorId(operator)
                .recipientIds(PmNotifyEvent.recipients(recipient))
                .detail("你被指派为该工作项的负责人")
                .build();
    }

    @Test
    @DisplayName("开关关闭：不发布事件、不产生任何调用")
    void disabledShouldBeNoop() {
        publisher.publish(assignEvent(2L, 1L));

        verifyNoInteractions(eventPublisher);
        verifyNoInteractions(notifyClient);
    }

    @Test
    @DisplayName("接收人为操作人：过滤后不发布")
    void skipSelfNotification() {
        properties.setEnabled(true);

        publisher.publish(assignEvent(1L, 1L));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("启用：捕获租户并发布事件，接收人过滤本人")
    void publishWithTenant() {
        properties.setEnabled(true);
        TenantContext.set(9L);
        PmNotifyEvent event = assignEvent(2L, 1L);
        event.setRecipientIds(PmNotifyEvent.recipients(2L, 1L, 2L));

        publisher.publish(event);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        PmNotifyEvent published = (PmNotifyEvent) captor.getValue();
        assertSame(event, published);
        assertEquals(List.of(2L), published.getRecipientIds());
        assertEquals(9L, published.getTenantId());
    }

    @Test
    @DisplayName("投递：标题取通知类型、正文含工作项与明细")
    void deliverShouldBuildMessage() {
        publisher.deliver(assignEvent(2L, 1L));

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(notifyClient).notify(eq(List.of(2L)), eq("工作项指派"), content.capture(),
                eq("PM"), eq("101"), anyString());
        assertEquals("PM-1 修复登录缺陷\n你被指派为该工作项的负责人", content.getValue());
    }

    @Test
    @DisplayName("投递失败：仅告警、不向调用方抛异常")
    void deliverShouldSwallowFailure() {
        doThrow(new IllegalStateException("im unavailable"))
                .when(notifyClient).notify(eq(List.of(2L)), anyString(), anyString(), anyString(),
                        anyString(), anyString());

        assertDoesNotThrow(() -> publisher.deliver(assignEvent(2L, 1L)));
        verify(notifyClient).notify(eq(List.of(2L)), anyString(), anyString(), anyString(), anyString(),
                anyString());
    }

    @Test
    @DisplayName("投递后清理线程租户，避免线程复用串租户")
    void deliverShouldClearTenant() {
        publisher.deliver(assignEvent(2L, 1L));

        assertNull(TenantContext.get());
    }
}
