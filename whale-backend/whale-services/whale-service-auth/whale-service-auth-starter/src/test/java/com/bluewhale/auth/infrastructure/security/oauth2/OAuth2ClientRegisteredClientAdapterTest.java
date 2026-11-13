package com.bluewhale.auth.infrastructure.security.oauth2;

import com.bluewhale.auth.domain.aggregate.OAuthClientAggregate;
import com.bluewhale.auth.domain.repository.OAuthClientRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link OAuth2ClientRegisteredClientAdapter} 单元测试（M1）。
 *
 * <p>覆盖领域聚合到框架 {@link RegisteredClient} 的翻译口径，以及停用客户端在协议层
 * 「等价于不存在」的语义。</p>
 *
 * @author BlueWhale
 */
class OAuth2ClientRegisteredClientAdapterTest {

    private static final Long CLIENT_PK = 26010L;
    private static final String CLIENT_ID = "bluewhale-web";
    private static final String SECRET_HASH = "$2b$12$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUV";
    private static final String REDIRECT_URI = "http://localhost:5173/oauth/callback";
    private static final String SCOPE = "sys:user:list";

    private final OAuthClientRepository repository = mock(OAuthClientRepository.class);
    private final OAuth2ClientRegisteredClientAdapter adapter = new OAuth2ClientRegisteredClientAdapter(repository);

    /**
     * 领域字段应完整翻译为框架注册客户端
     */
    @Test
    @DisplayName("领域聚合翻译为 RegisteredClient：标识/密钥/授权类型/回调/scope/TTL/确认标记")
    void shouldTranslateDomainAggregateToRegisteredClient() {
        when(repository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(client(OAuthClientAggregate.STATUS_ENABLED)));

        RegisteredClient registeredClient = adapter.findByClientId(CLIENT_ID);

        assertEquals(String.valueOf(CLIENT_PK), registeredClient.getId());
        assertEquals(CLIENT_ID, registeredClient.getClientId());
        assertEquals(SECRET_HASH, registeredClient.getClientSecret());
        assertTrue(registeredClient.getAuthorizationGrantTypes().contains(AuthorizationGrantType.AUTHORIZATION_CODE));
        assertTrue(registeredClient.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN));
        assertTrue(registeredClient.getRedirectUris().contains(REDIRECT_URI));
        assertTrue(registeredClient.getScopes().contains(SCOPE));
        assertTrue(registeredClient.getClientSettings().isRequireAuthorizationConsent());
        assertFalse(registeredClient.getClientSettings().isRequireProofKey());
        assertEquals(Duration.ofSeconds(7200), registeredClient.getTokenSettings().getAccessTokenTimeToLive());
        assertEquals(Duration.ofSeconds(604800), registeredClient.getTokenSettings().getRefreshTokenTimeToLive());
    }

    /**
     * 停用客户端对协议层不可见
     */
    @Test
    @DisplayName("停用客户端在协议层返回 null（等价于客户端不存在）")
    void shouldHideDisabledClient() {
        when(repository.findByClientId(CLIENT_ID))
                .thenReturn(Optional.of(client(OAuthClientAggregate.STATUS_DISABLED)));

        assertNull(adapter.findByClientId(CLIENT_ID));
    }

    /**
     * 框架侧主键为领域自增主键的字符串形式
     */
    @Test
    @DisplayName("findById 按领域主键查询；非法主键返回 null")
    void shouldFindByIdWithNumericPrimaryKey() {
        when(repository.findById(CLIENT_PK))
                .thenReturn(Optional.of(client(OAuthClientAggregate.STATUS_ENABLED)));

        assertEquals(CLIENT_ID, adapter.findById(String.valueOf(CLIENT_PK)).getClientId());
        assertNull(adapter.findById("not-a-number"));
        assertNull(adapter.findById(null));
    }

    /**
     * 客户端写入必须走领域应用服务，框架不得绕过
     */
    @Test
    @DisplayName("save 拒绝写入：客户端生命周期由 OAuthClientAppService 管理")
    void shouldRejectSave() {
        assertThrows(UnsupportedOperationException.class, () -> adapter.save(null));
    }

    /**
     * 构造启用/停用的客户端聚合
     *
     * @param status 状态
     * @return 客户端聚合
     */
    private static OAuthClientAggregate client(int status) {
        return OAuthClientAggregate.reconstitute(CLIENT_PK, CLIENT_ID, SECRET_HASH, "BlueWhale Web 管理端",
                REDIRECT_URI, "authorization_code,refresh_token", SCOPE, 7200, 604800, 0, status,
                LocalDateTime.now(), LocalDateTime.now());
    }
}
