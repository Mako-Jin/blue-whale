package com.bluewhale.gateway.whitelist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link GatewayWhitelistProvider} 单元测试：成功替换、空结果清空、失败保留快照、
 * 方法匹配与防重入。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class GatewayWhitelistProviderTest {

    private static final String PATH = "/demo/open/ping";

    @Mock
    private AuthGatewayWhitelistClient client;

    private GatewayWhitelistDynamicProperties properties;

    private GatewayWhitelistProvider provider;

    @BeforeEach
    void setUp() {
        properties = new GatewayWhitelistDynamicProperties();
        provider = new GatewayWhitelistProvider(properties, client);
    }

    @Test
    @DisplayName("refresh：拉取成功后命中远程规则")
    void shouldReplaceSnapshotOnSuccess() {
        when(client.fetch()).thenReturn(Mono.just(List.of(new GatewayWhitelistRule("/demo/open/**", "*"))));

        provider.refresh();

        assertTrue(provider.matches(PATH, "GET"));
        assertFalse(provider.matches("/demo/closed/ping", "GET"));
    }

    @Test
    @DisplayName("refresh：拉取成功但为空时清空快照（回落本地配置）")
    void shouldClearSnapshotOnEmptyResult() {
        when(client.fetch()).thenReturn(Mono.just(List.of(new GatewayWhitelistRule("/demo/open/**", "*"))));
        provider.refresh();
        assertTrue(provider.matches(PATH, "GET"));

        when(client.fetch()).thenReturn(Mono.just(List.of()));
        provider.refresh();

        assertFalse(provider.matches(PATH, "GET"));
    }

    @Test
    @DisplayName("refresh：拉取失败时保留上一份快照")
    void shouldKeepSnapshotOnFailure() {
        when(client.fetch()).thenReturn(Mono.just(List.of(new GatewayWhitelistRule("/demo/open/**", "*"))));
        provider.refresh();

        when(client.fetch()).thenReturn(Mono.error(new IllegalStateException("auth unavailable")));
        provider.refresh();

        assertTrue(provider.matches(PATH, "GET"));
    }

    @Test
    @DisplayName("matches：方法不匹配时不放行")
    void shouldNotMatchWhenMethodDiffers() {
        when(client.fetch()).thenReturn(Mono.just(List.of(new GatewayWhitelistRule("/demo/open/**", "GET"))));
        provider.refresh();

        assertTrue(provider.matches(PATH, "GET"));
        assertFalse(provider.matches(PATH, "POST"));
    }

    @Test
    @DisplayName("refresh：上一次未结束时跳过本次刷新")
    void shouldSkipWhenPreviousRefreshInFlight() {
        when(client.fetch()).thenReturn(Mono.never());

        provider.refresh();
        provider.refresh();

        verify(client, times(1)).fetch();
    }

    @Test
    @DisplayName("refresh：动态加载关闭时不发起拉取")
    void shouldSkipWhenDisabled() {
        properties.setEnabled(false);

        provider.refresh();

        verifyNoInteractions(client);
        assertFalse(provider.matches(PATH, "GET"));
    }
}
