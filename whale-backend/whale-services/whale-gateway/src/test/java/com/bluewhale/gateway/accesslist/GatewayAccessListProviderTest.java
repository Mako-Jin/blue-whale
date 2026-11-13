package com.bluewhale.gateway.accesslist;

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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link GatewayAccessListProvider} 单元测试：白名单/黑名单各自独立刷新
 * （成功替换、空结果清空、失败保留快照）、方法匹配、防重入与开关。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class GatewayAccessListProviderTest {

    private static final String PATH = "/demo/open/ping";

    @Mock
    private AuthGatewayAccessListClient client;

    private GatewayAccessListProperties properties;

    private GatewayAccessListProvider provider;

    @BeforeEach
    void setUp() {
        properties = new GatewayAccessListProperties();
        provider = new GatewayAccessListProvider(properties, client);
        lenient().when(client.fetchWhitelist()).thenReturn(Mono.just(List.of()));
        lenient().when(client.fetchBlacklist()).thenReturn(Mono.just(List.of()));
    }

    @Test
    @DisplayName("refresh：白名单拉取成功后命中远程规则")
    void shouldReplaceWhitelistOnSuccess() {
        when(client.fetchWhitelist())
                .thenReturn(Mono.just(List.of(GatewayAccessListRule.platform("/demo/open/**", "*"))));

        provider.refresh();

        assertTrue(provider.matchesWhitelist(PATH, "GET"));
        assertFalse(provider.matchesWhitelist("/demo/closed/ping", "GET"));
    }

    @Test
    @DisplayName("refresh：白名单拉取成功但为空时清空快照（回落本地配置）")
    void shouldClearWhitelistOnEmptyResult() {
        when(client.fetchWhitelist())
                .thenReturn(Mono.just(List.of(GatewayAccessListRule.platform("/demo/open/**", "*"))));
        provider.refresh();
        assertTrue(provider.matchesWhitelist(PATH, "GET"));

        when(client.fetchWhitelist()).thenReturn(Mono.just(List.of()));
        provider.refresh();

        assertFalse(provider.matchesWhitelist(PATH, "GET"));
    }

    @Test
    @DisplayName("refresh：白名单拉取失败时保留上一份快照")
    void shouldKeepWhitelistOnFailure() {
        when(client.fetchWhitelist())
                .thenReturn(Mono.just(List.of(GatewayAccessListRule.platform("/demo/open/**", "*"))));
        provider.refresh();

        when(client.fetchWhitelist()).thenReturn(Mono.error(new IllegalStateException("auth unavailable")));
        provider.refresh();

        assertTrue(provider.matchesWhitelist(PATH, "GET"));
    }

    @Test
    @DisplayName("matchesWhitelist：方法不匹配时不放行")
    void shouldNotMatchWhitelistWhenMethodDiffers() {
        when(client.fetchWhitelist())
                .thenReturn(Mono.just(List.of(GatewayAccessListRule.platform("/demo/open/**", "GET"))));
        provider.refresh();

        assertTrue(provider.matchesWhitelist(PATH, "GET"));
        assertFalse(provider.matchesWhitelist(PATH, "POST"));
    }

    @Test
    @DisplayName("refresh：黑名单拉取成功后命中封禁规则")
    void shouldReplaceBlacklistOnSuccess() {
        when(client.fetchBlacklist())
                .thenReturn(Mono.just(List.of(GatewayAccessListRule.platform("/pay/**", "*"))));

        provider.refresh();

        assertTrue(provider.matchesBlacklist("/pay/v1/order", "POST"));
        assertFalse(provider.matchesBlacklist("/demo/open/ping", "POST"));
    }

    @Test
    @DisplayName("refresh：黑名单拉取成功但为空时解除全部封禁")
    void shouldClearBlacklistOnEmptyResult() {
        when(client.fetchBlacklist())
                .thenReturn(Mono.just(List.of(GatewayAccessListRule.platform("/pay/**", "*"))));
        provider.refresh();
        assertTrue(provider.matchesBlacklist("/pay/v1/order", "POST"));

        when(client.fetchBlacklist()).thenReturn(Mono.just(List.of()));
        provider.refresh();

        assertFalse(provider.matchesBlacklist("/pay/v1/order", "POST"));
    }

    @Test
    @DisplayName("refresh：黑名单拉取失败时保留上一份封禁快照")
    void shouldKeepBlacklistOnFailure() {
        when(client.fetchBlacklist())
                .thenReturn(Mono.just(List.of(GatewayAccessListRule.platform("/pay/**", "*"))));
        provider.refresh();

        when(client.fetchBlacklist()).thenReturn(Mono.error(new IllegalStateException("auth unavailable")));
        provider.refresh();

        assertTrue(provider.matchesBlacklist("/pay/v1/order", "POST"));
    }

    @Test
    @DisplayName("refresh：白名单失败不影响黑名单刷新（独立降级）")
    void shouldRefreshBlacklistIndependently() {
        when(client.fetchWhitelist()).thenReturn(Mono.error(new IllegalStateException("timeout")));
        when(client.fetchBlacklist())
                .thenReturn(Mono.just(List.of(GatewayAccessListRule.platform("/pay/**", "*"))));

        provider.refresh();

        assertTrue(provider.matchesBlacklist("/pay/v1/order", "GET"));
        assertFalse(provider.matchesWhitelist("/pay/v1/order", "GET"));
    }

    @Test
    @DisplayName("应急黑名单：不限 HTTP 方法，命中即封禁")
    void shouldMatchEmergencyBlacklistForAnyMethod() {
        properties.setEmergencyBlacklist(List.of("/pay/**"));

        assertTrue(provider.matchesBlacklist("/pay/v1/order", "GET"));
        assertTrue(provider.matchesBlacklist("/pay/v1/order", "POST"));
        assertFalse(provider.matchesBlacklist("/demo/open/ping", "GET"));
    }

    @Test
    @DisplayName("应急黑名单：动态拉取关闭时仍生效且不发起远程拉取")
    void shouldMatchEmergencyBlacklistWhenDisabled() {
        properties.setEnabled(false);
        properties.setEmergencyBlacklist(List.of(" /pay/** "));

        provider.refresh();

        verifyNoInteractions(client);
        assertTrue(provider.matchesBlacklist("/pay/v1/order", "DELETE"));
    }

    @Test
    @DisplayName("应急黑名单：空白项忽略，不产生误封")
    void shouldIgnoreBlankEmergencyPatterns() {
        properties.setEmergencyBlacklist(List.of("  ", ""));

        assertFalse(provider.matchesBlacklist("/pay/v1/order", "GET"));
    }

    @Test
    @DisplayName("refresh：同一名单上一次未结束时跳过本次刷新")
    void shouldSkipWhenPreviousRefreshInFlight() {
        when(client.fetchWhitelist()).thenReturn(Mono.never());

        provider.refresh();
        provider.refresh();

        verify(client, times(1)).fetchWhitelist();
        verify(client, times(2)).fetchBlacklist();
    }

    @Test
    @DisplayName("refresh：动态加载关闭时不发起拉取")
    void shouldSkipWhenDisabled() {
        properties.setEnabled(false);

        provider.refresh();

        verifyNoInteractions(client);
        assertFalse(provider.matchesWhitelist(PATH, "GET"));
        assertFalse(provider.matchesBlacklist(PATH, "GET"));
    }

    @Test
    @DisplayName("matchesWhitelist：租户级规则不参与平台级匹配（免认证放行不受租户规则影响）")
    void tenantScopedRuleShouldNotAffectPlatformWhitelist() {
        when(client.fetchWhitelist())
                .thenReturn(Mono.just(List.of(new GatewayAccessListRule("/demo/open/**", "*", "TENANT", 5L))));

        provider.refresh();

        // 免认证放行判定不携带租户上下文，租户级规则只在已验签令牌场景参与匹配
        assertFalse(provider.matchesWhitelist(PATH, "GET"));
    }

    @Test
    @DisplayName("matchesTenantWhitelist：绑定租户的令牌命中租户级与平台级规则")
    void tenantWhitelistShouldMatchScopedAndPlatformRules() {
        when(client.fetchWhitelist()).thenReturn(Mono.just(List.of(
                new GatewayAccessListRule("/demo/open/**", "*", "TENANT", 5L),
                GatewayAccessListRule.platform("/demo/platform/**", "*"))));

        provider.refresh();

        // 租户 5：命中租户级规则
        assertTrue(provider.matchesTenantWhitelist(PATH, "GET", "5"));
        // 租户 6：不命中租户 5 的规则
        assertFalse(provider.matchesTenantWhitelist(PATH, "GET", "6"));
        // 平台级规则对所有租户生效
        assertTrue(provider.matchesTenantWhitelist("/demo/platform/ping", "GET", "6"));
        // 租户 ID 非法时不匹配租户级规则
        assertFalse(provider.matchesTenantWhitelist(PATH, "GET", "not-a-number"));
        assertFalse(provider.matchesTenantWhitelist(PATH, "GET", null));
    }
}
