package com.bluewhale.auth.domain.aggregate;

import com.bluewhale.auth.domain.model.vo.GrantType;
import com.bluewhale.common.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link OAuthClientAggregate} 核心不变量单元测试（权限中心扩展 M0）。
 *
 * @author BlueWhale
 */
class OAuthClientAggregateTest {

    private static final List<String> REDIRECTS = List.of("https://app.example.com/callback");

    @Test
    @DisplayName("注册授权码客户端：默认启用且支持回调精确匹配")
    void shouldCreateAuthorizationCodeClient() {
        OAuthClientAggregate client = OAuthClientAggregate.create("bluewhale-web", "$2a$12$hash",
                "BlueWhale Web", REDIRECTS, List.of("authorization_code", "refresh_token"),
                List.of("openid", "profile"), 7200, 604800, false);

        assertThat(client.isEnabled()).isTrue();
        assertThat(client.supports(GrantType.AUTHORIZATION_CODE)).isTrue();
        assertThat(client.allowsRedirectUri("https://app.example.com/callback")).isTrue();
        assertThat(client.allowsRedirectUri("https://app.example.com/callback/extra")).isFalse();
    }

    @Test
    @DisplayName("授权码模式缺少回调地址白名单时拒绝注册")
    void shouldRejectAuthorizationCodeWithoutRedirectUris() {
        assertThrows(DomainException.class, () -> OAuthClientAggregate.create("bluewhale-web", "$2a$12$hash",
                "BlueWhale Web", List.of(), List.of("authorization_code"),
                List.of("openid"), 7200, 604800, false));
    }

    @Test
    @DisplayName("回调地址含通配符时拒绝注册")
    void shouldRejectWildcardRedirectUri() {
        assertThrows(DomainException.class, () -> OAuthClientAggregate.create("bluewhale-web", "$2a$12$hash",
                "BlueWhale Web", List.of("https://*.example.com/callback"), List.of("authorization_code"),
                List.of("openid"), 7200, 604800, false));
    }

    @Test
    @DisplayName("password / implicit 授权类型一律拒绝")
    void shouldRejectRemovedGrantTypes() {
        assertThrows(DomainException.class, () -> OAuthClientAggregate.create("bluewhale-web", "$2a$12$hash",
                "BlueWhale Web", REDIRECTS, List.of("password"),
                List.of("openid"), 7200, 604800, false));
        assertThrows(DomainException.class, () -> OAuthClientAggregate.create("bluewhale-web", "$2a$12$hash",
                "BlueWhale Web", REDIRECTS, List.of("implicit"),
                List.of("openid"), 7200, 604800, false));
    }

    @Test
    @DisplayName("仅客户端凭证模式允许不配置回调地址")
    void shouldAllowClientCredentialsWithoutRedirectUris() {
        OAuthClientAggregate client = OAuthClientAggregate.create("bw_service", "$2a$12$hash",
                "Service Client", List.of(), List.of("client_credentials"),
                List.of("sys:user:list"), 300, 600, false);

        assertThat(client.getRedirectUris().isEmpty()).isTrue();
        assertThat(client.supports(GrantType.CLIENT_CREDENTIALS)).isTrue();
    }

    @Test
    @DisplayName("停用后不得作为换票入口，轮换密钥只替换哈希")
    void shouldDisableAndRotateSecret() {
        OAuthClientAggregate client = OAuthClientAggregate.create("bluewhale-web", "$2a$12$old",
                "BlueWhale Web", REDIRECTS, List.of("authorization_code"),
                List.of("openid"), 7200, 604800, false);

        client.disable();
        client.changeSecret("$2a$12$new");

        assertThat(client.isEnabled()).isFalse();
        assertThat(client.getClientSecretHash()).isEqualTo("$2a$12$new");
    }
}
