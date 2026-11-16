package com.bluewhale.gateway.route;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link RouteOverrideProvider} 单元测试（GW-10 Phase 2）：快照替换与刷新事件、
 * 失败保留快照、防重入与开关。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class RouteOverrideProviderTest {

    @Mock
    private AuthGatewayRouteOverrideClient client;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private RouteOverrideProperties properties;

    private RouteOverrideProvider provider;

    @BeforeEach
    void setUp() {
        properties = new RouteOverrideProperties();
        provider = new RouteOverrideProvider(properties, client, eventPublisher);
    }

    @Test
    @DisplayName("refresh：拉取成功后替换快照并发布路由刷新事件")
    void shouldReplaceSnapshotAndPublishEventOnSuccess() {
        when(client.fetch()).thenReturn(Mono.just(List.of(
                new RouteOverrideRule("whale-demo", false, null, null, 1L))));

        provider.refresh();

        Map<String, RouteOverrideRule> overrides = provider.overrides();
        assertEquals(1, overrides.size());
        assertFalse(overrides.get("whale-demo").isEffectiveAt(java.time.LocalDateTime.now()));
        verifyRefreshEvents(1);
    }

    @Test
    @DisplayName("refresh：内容未变化时不重复发布路由刷新事件")
    void shouldNotPublishEventWhenContentUnchanged() {
        when(client.fetch()).thenReturn(Mono.just(List.of(
                new RouteOverrideRule("whale-demo", false, null, null, 1L))));

        provider.refresh();
        provider.refresh();

        verifyRefreshEvents(1);
    }

    @Test
    @DisplayName("refresh：快照从有覆盖恢复为空时同样发布刷新事件（解除停用）")
    void shouldPublishEventWhenSnapshotCleared() {
        when(client.fetch()).thenReturn(Mono.just(List.of(
                new RouteOverrideRule("whale-demo", false, null, null, 1L))));
        provider.refresh();

        when(client.fetch()).thenReturn(Mono.just(List.of()));
        provider.refresh();

        verifyRefreshEvents(2);
        assertTrue(provider.overrides().isEmpty());
    }

    @Test
    @DisplayName("refresh：拉取失败时保留上一份快照且不发刷新事件")
    void shouldKeepSnapshotOnFailure() {
        when(client.fetch()).thenReturn(Mono.just(List.of(
                new RouteOverrideRule("whale-demo", false, null, null, 1L))));
        provider.refresh();

        when(client.fetch()).thenReturn(Mono.error(new IllegalStateException("auth unavailable")));
        provider.refresh();

        assertEquals(1, provider.overrides().size());
        verifyRefreshEvents(1);
    }

    @Test
    @DisplayName("refresh：同一次拉取未结束时跳过本次刷新")
    void shouldSkipWhenPreviousRefreshInFlight() {
        when(client.fetch()).thenReturn(Mono.never());

        provider.refresh();
        provider.refresh();

        verify(client, times(1)).fetch();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("refresh：覆盖层关闭时不发起拉取")
    void shouldSkipWhenDisabled() {
        properties.setEnabled(false);

        provider.refresh();

        verifyNoInteractions(client);
        assertTrue(provider.overrides().isEmpty());
        assertFalse(provider.isOverrideEnabled());
    }

    @Test
    @DisplayName("refresh：拉取客户端同步抛异常时复位防重入标记，后续刷新不被永久跳过")
    void shouldResetRefreshFlagWhenSourceThrowsSynchronously() {
        when(client.fetch())
                .thenThrow(new IllegalArgumentException("URI is not absolute"))
                .thenReturn(Mono.just(List.of(new RouteOverrideRule("whale-demo", true, null, null, 1L))));

        provider.refresh();
        provider.refresh();

        verify(client, times(2)).fetch();
        assertEquals(1, provider.overrides().size());
    }

    @Test
    @DisplayName("overrides：按 routeId 索引，生效窗口内的规则判定生效")
    void shouldIndexOverridesByRouteId() {
        lenient().when(client.fetch()).thenReturn(Mono.just(List.of(
                new RouteOverrideRule("whale-a", true, null, null, 1L),
                new RouteOverrideRule("whale-b", true,
                        java.time.LocalDateTime.now().minusHours(1),
                        java.time.LocalDateTime.now().plusHours(1), 2L))));

        provider.refresh();

        Map<String, RouteOverrideRule> overrides = provider.overrides();
        assertEquals(2, overrides.size());
        assertTrue(overrides.get("whale-a").isEffectiveAt(java.time.LocalDateTime.now()));
        assertTrue(overrides.get("whale-b").isEffectiveAt(java.time.LocalDateTime.now()));
    }

    /**
     * 校验路由刷新事件的发布次数（{@code RefreshRoutesEvent} 未重写 equals，按事件 source 判定）
     *
     * @param expectedTimes 期望发布次数
     */
    private void verifyRefreshEvents(int expectedTimes) {
        verify(eventPublisher, times(expectedTimes)).publishEvent(argThat(event -> event instanceof RefreshRoutesEvent
                && ((RefreshRoutesEvent) event).getSource() == provider));
    }
}
