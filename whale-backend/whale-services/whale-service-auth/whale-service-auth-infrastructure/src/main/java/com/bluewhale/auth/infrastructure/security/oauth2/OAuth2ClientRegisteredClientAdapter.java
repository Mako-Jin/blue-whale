package com.bluewhale.auth.infrastructure.security.oauth2;

import com.bluewhale.auth.domain.aggregate.OAuthClientAggregate;
import com.bluewhale.auth.domain.model.vo.GrantType;
import com.bluewhale.auth.domain.repository.OAuthClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;

/**
 * OAuth2 客户端仓储适配器（M1）：把领域聚合 {@link OAuthClientAggregate} 翻译为
 * Spring Authorization Server 所需的 {@link RegisteredClient}。
 *
 * <p><b>为什么不由框架直接读写库</b>：客户端注册信息以 {@code sys_oauth_client} 为唯一事实源，
 * 生命周期由 {@code OAuthClientAppService} 维护（含密钥轮换、审计、停用）。因此本适配器
 * 只做「读」的翻译，{@link #save(RegisteredClient)} 一律拒绝，避免出现双写源。</p>
 *
 * <p><b>停用语义</b>：停用客户端在协议层等价于「客户端不存在」，返回 {@code null}，
 * 授权端点与令牌端点均按 {@code invalid_client} 拒绝，已签发的刷新令牌随之无法续期。</p>
 *
 * <p>本类位于基础设施层，允许依赖 Spring Security；领域层不感知这些类型。</p>
 *
 * @author BlueWhale
 */
public class OAuth2ClientRegisteredClientAdapter implements RegisteredClientRepository {

    private final OAuthClientRepository oauthClientRepository;

    /**
     * 构造适配器
     *
     * @param oauthClientRepository 领域客户端仓储
     */
    public OAuth2ClientRegisteredClientAdapter(OAuthClientRepository oauthClientRepository) {
        this.oauthClientRepository = oauthClientRepository;
    }

    /**
     * 客户端写入由领域应用服务负责，禁止框架绕过领域规则直接落库
     *
     * @param registeredClient 框架侧客户端
     * @throws UnsupportedOperationException 恒定抛出
     */
    @Override
    public void save(RegisteredClient registeredClient) {
        throw new UnsupportedOperationException("OAuth 客户端生命周期由 OAuthClientAppService 管理，禁止经 RegisteredClientRepository 写入");
    }

    /**
     * 按框架侧主键查询（主键即 {@code sys_oauth_client.id} 的字符串形式）
     *
     * @param id 客户端主键
     * @return 注册客户端；不存在或已停用返回 {@code null}
     */
    @Override
    public RegisteredClient findById(String id) {
        Long primaryKey = parsePrimaryKey(id);
        if (primaryKey == null) {
            return null;
        }
        return oauthClientRepository.findById(primaryKey)
                .filter(OAuthClientAggregate::isEnabled)
                .map(this::toRegisteredClient)
                .orElse(null);
    }

    /**
     * 按协议 client_id 查询
     *
     * @param clientId 客户端标识
     * @return 注册客户端；不存在或已停用返回 {@code null}
     */
    @Override
    public RegisteredClient findByClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        return oauthClientRepository.findByClientId(clientId)
                .filter(OAuthClientAggregate::isEnabled)
                .map(this::toRegisteredClient)
                .orElse(null);
    }

    /**
     * 领域聚合 → 框架注册客户端
     *
     * @param client 客户端聚合
     * @return 框架侧注册客户端
     */
    RegisteredClient toRegisteredClient(OAuthClientAggregate client) {
        RegisteredClient.Builder builder = RegisteredClient.withId(String.valueOf(client.getId()))
                .clientId(client.getClientId().value())
                .clientSecret(client.getClientSecretHash())
                .clientName(client.getClientName())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST);
        client.getGrantTypes().forEach(grantType -> builder.authorizationGrantType(toAuthorizationGrantType(grantType)));
        client.getRedirectUris().values().forEach(builder::redirectUri);
        client.getScopes().values().forEach(builder::scope);
        return builder
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(!client.isAutoApprove())
                        .requireProofKey(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofSeconds(client.getAccessTokenTtl()))
                        .refreshTokenTimeToLive(Duration.ofSeconds(client.getRefreshTokenTtl()))
                        .reuseRefreshTokens(true)
                        .build())
                .build();
    }

    /**
     * 领域授权类型 → 框架授权类型
     *
     * @param grantType 领域授权类型
     * @return 框架授权类型
     */
    private static AuthorizationGrantType toAuthorizationGrantType(GrantType grantType) {
        return switch (grantType) {
            case AUTHORIZATION_CODE -> AuthorizationGrantType.AUTHORIZATION_CODE;
            case CLIENT_CREDENTIALS -> AuthorizationGrantType.CLIENT_CREDENTIALS;
            case REFRESH_TOKEN -> AuthorizationGrantType.REFRESH_TOKEN;
        };
    }

    /**
     * 把框架侧字符串主键还原为领域主键
     *
     * @param id 框架侧主键
     * @return 领域主键；非数字返回 {@code null}
     */
    private static Long parsePrimaryKey(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
