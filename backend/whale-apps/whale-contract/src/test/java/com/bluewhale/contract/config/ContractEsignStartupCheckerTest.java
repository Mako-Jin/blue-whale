package com.bluewhale.contract.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 合同电子签生产配置启动校验测试。
 *
 * @author BlueWhale
 */
class ContractEsignStartupCheckerTest {

    /** 待校验的电子签配置 */
    private ContractEsignProperties properties;

    /** 可切换激活 profile 的测试环境 */
    private MockEnvironment environment;

    /** 被测启动校验器 */
    private ContractEsignStartupChecker checker;

    /**
     * 每个用例重置配置与环境，避免用例间相互影响。
     */
    @BeforeEach
    void setUp() {
        properties = new ContractEsignProperties();
        environment = new MockEnvironment();
        checker = new ContractEsignStartupChecker(properties, environment);
    }

    /**
     * dev 环境允许 local 自签与默认回调密钥，保持本地联调能力。
     */
    @Test
    void devProfileAllowsLocalAndDefaultSecret() {
        environment.setActiveProfiles("dev");

        assertDoesNotThrow(() -> checker.afterPropertiesSet());
    }

    /**
     * 非 dev 环境默认的 local 渠道必须拒绝启动。
     */
    @Test
    void nonDevRejectsLocalProvider() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> checker.afterPropertiesSet());

        assertTrue(exception.getMessage().contains("local"));
    }

    /**
     * 非 dev 环境渠道为空时按 local 处理并拒绝启动。
     */
    @Test
    void nonDevRejectsBlankProvider() {
        properties.setProvider("   ");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> checker.afterPropertiesSet());

        assertTrue(exception.getMessage().contains("CONTRACT_ESIGN_PROVIDER"));
    }

    /**
     * 非 dev 环境使用真实渠道但回调密钥仍为默认值时必须拒绝启动。
     */
    @Test
    void nonDevRejectsDefaultCallbackSecret() {
        properties.setProvider("esignbao");
        configureProvider("esignbao");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> checker.afterPropertiesSet());

        assertTrue(exception.getMessage().contains("CONTRACT_ESIGN_CALLBACK_SECRET"));
    }

    /**
     * 非 dev 环境选择三方渠道但未配置接入参数时必须拒绝启动。
     */
    @Test
    void nonDevRejectsProviderWithoutCredentials() {
        properties.setProvider("esignbao");
        properties.setCallbackSecret("prod-contract-callback-secret-2026");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> checker.afterPropertiesSet());

        assertTrue(exception.getMessage().contains("providers.esignbao"));
    }

    /**
     * 非 dev 环境配置真实渠道与自定义回调密钥时允许启动。
     */
    @Test
    void nonDevAcceptsRealProviderAndCustomSecret() {
        properties.setProvider("esignbao");
        properties.setCallbackSecret("prod-contract-callback-secret-2026");
        configureProvider("esignbao");

        assertDoesNotThrow(() -> checker.afterPropertiesSet());
    }

    /**
     * 为渠道补充可用的开放平台接入参数。
     *
     * @param provider 渠道名
     */
    private void configureProvider(String provider) {
        ContractEsignProperties.ProviderConfig config = new ContractEsignProperties.ProviderConfig();
        config.setBaseUrl("https://esign.example.com");
        config.setAppId("app-1");
        config.setAppSecret("secret-1");
        properties.getProviders().put(provider, config);
    }
}
