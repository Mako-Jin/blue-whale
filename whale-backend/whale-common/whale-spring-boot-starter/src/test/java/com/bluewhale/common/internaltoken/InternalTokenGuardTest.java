package com.bluewhale.common.internaltoken;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link InternalTokenGuard} 单元测试（V-02）：非 dev 未配置令牌拒绝启动、
 * dev 告警放行、显式关闭放行、已配置令牌放行。
 *
 * @author BlueWhale
 */
class InternalTokenGuardTest {

    @Test
    @DisplayName("guard：非 dev 环境令牌为空时拒绝启动")
    void shouldFailFastWhenTokenBlankInNonDevProfile() {
        InternalTokenProperties properties = new InternalTokenProperties();
        properties.setToken("");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThrows(IllegalStateException.class, () -> new InternalTokenGuard(properties, environment).afterPropertiesSet());
    }

    @Test
    @DisplayName("guard：dev 环境令牌为空时仅告警不抛异常")
    void shouldWarnOnlyWhenTokenBlankInDevProfile() {
        InternalTokenProperties properties = new InternalTokenProperties();
        properties.setToken("");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        assertDoesNotThrow(() -> new InternalTokenGuard(properties, environment).afterPropertiesSet());
    }

    @Test
    @DisplayName("guard：显式关闭校验时不校验令牌")
    void shouldSkipWhenDisabled() {
        InternalTokenProperties properties = new InternalTokenProperties();
        properties.setEnabled(false);
        properties.setToken("");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertDoesNotThrow(() -> new InternalTokenGuard(properties, environment).afterPropertiesSet());
    }

    @Test
    @DisplayName("guard：已配置令牌时放行")
    void shouldPassWhenTokenConfigured() {
        InternalTokenProperties properties = new InternalTokenProperties();
        properties.setToken("secret-token");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertDoesNotThrow(() -> new InternalTokenGuard(properties, environment).afterPropertiesSet());
    }

    @Test
    @DisplayName("guard：按服务签发配置合法时放行")
    void shouldPassWhenIssuersValid() {
        InternalTokenProperties properties = new InternalTokenProperties();
        properties.setIssuers(issuers("mall", "secret", List.of("1001"), false));

        assertDoesNotThrow(() -> new InternalTokenGuard(properties, prod()).afterPropertiesSet());
    }

    @Test
    @DisplayName("guard：非平台服务未配置租户白名单时拒绝启动")
    void shouldFailWhenIssuerLacksTenants() {
        InternalTokenProperties properties = new InternalTokenProperties();
        properties.setIssuers(issuers("mall", "secret", List.of(), false));

        assertThrows(IllegalStateException.class,
                () -> new InternalTokenGuard(properties, prod()).afterPropertiesSet());
    }

    @Test
    @DisplayName("guard：服务密钥为空时拒绝启动")
    void shouldFailWhenIssuerSecretBlank() {
        InternalTokenProperties properties = new InternalTokenProperties();
        properties.setIssuers(issuers("mall", " ", List.of("1001"), false));

        assertThrows(IllegalStateException.class,
                () -> new InternalTokenGuard(properties, prod()).afterPropertiesSet());
    }

    @Test
    @DisplayName("guard：调用方身份只配置一半时拒绝启动")
    void shouldFailWhenCallerIdentityIncomplete() {
        InternalTokenProperties properties = new InternalTokenProperties();
        properties.setIssuerId("mall");
        properties.setIssuerSecret("");

        assertThrows(IllegalStateException.class,
                () -> new InternalTokenGuard(properties, prod()).afterPropertiesSet());
    }

    @Test
    @DisplayName("guard：服务 ID 含分隔符时拒绝启动")
    void shouldFailWhenIssuerIdContainsDot() {
        InternalTokenProperties properties = new InternalTokenProperties();
        properties.setIssuerId("mall.v2");
        properties.setIssuerSecret("secret");

        assertThrows(IllegalStateException.class,
                () -> new InternalTokenGuard(properties, prod()).afterPropertiesSet());
    }

    @Test
    @DisplayName("guard：出站令牌优先使用本服务身份")
    void shouldResolveOutboundTokenFromIdentity() {
        InternalTokenProperties properties = new InternalTokenProperties();
        properties.setToken("shared");
        properties.setIssuerId("mall");
        properties.setIssuerSecret("secret");

        assertEquals("mall.secret", properties.resolveOutboundToken());
    }

    /**
     * 构造服务令牌清单
     *
     * @param serviceId 服务 ID
     * @param secret    密钥
     * @param tenants   租户白名单
     * @param platform  是否平台级
     * @return 服务令牌清单
     */
    private static Map<String, InternalTokenProperties.Issuer> issuers(
            String serviceId, String secret, List<String> tenants, boolean platform) {
        InternalTokenProperties.Issuer issuer = new InternalTokenProperties.Issuer();
        issuer.setSecret(secret);
        issuer.setTenants(tenants);
        issuer.setPlatform(platform);
        Map<String, InternalTokenProperties.Issuer> issuers = new LinkedHashMap<>();
        issuers.put(serviceId, issuer);
        return issuers;
    }

    /**
     * 构造 prod 环境
     *
     * @return 环境
     */
    private static MockEnvironment prod() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        return environment;
    }
}
