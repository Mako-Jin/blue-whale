package com.bluewhale.common.security.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.env.MockPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ConfigEncryptionEnvironmentPostProcessor} 单测：属性源包装、密文检测与缺密钥 fail-fast。
 *
 * @author BlueWhale
 */
class ConfigEncryptionEnvironmentPostProcessorTest {

    /** 测试密钥（≥16 字符） */
    private static final String KEY = "blue-whale-test-key-0123456789";

    /** EPP 实例 */
    private final ConfigEncryptionEnvironmentPostProcessor postProcessor = new ConfigEncryptionEnvironmentPostProcessor();

    @Test
    @DisplayName("存在 ENC(...) 且密钥可用：属性读取返回明文，属性名仍可枚举")
    void shouldDecryptProperty() {
        String encrypted = new ConfigValueCipher(ConfigEncryptionSettings.of(KEY)).encrypt("s3cret");
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MockPropertySource("test-source")
                .withProperty("whale.config.encryption.password", KEY)
                .withProperty("demo.secret", encrypted));

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("demo.secret")).isEqualTo("s3cret");
        assertThat(environment.getPropertySources().contains("test-source")).isTrue();
        assertThat(environment.getPropertySources().get("test-source")).isInstanceOfAny(
                EncryptedPropertySource.class, EncryptedEnumerablePropertySource.class);
    }

    @Test
    @DisplayName("存在 ENC(...) 但缺密钥：启动失败且提示使用 WHALE_CONFIG_ENC_KEY")
    void shouldFailWithoutKey() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MockPropertySource("test-source")
                .withProperty("demo.secret", "ENC(abc)"));

        assertThatThrownBy(() -> postProcessor.postProcessEnvironment(environment, new SpringApplication()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ConfigEncryptionSettings.ENV_KEY);
    }

    @Test
    @DisplayName("关闭开关：不做包装，密文原样保留")
    void shouldSkipWhenDisabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MockPropertySource("test-source")
                .withProperty("whale.config.encryption.enabled", "false")
                .withProperty("whale.config.encryption.password", KEY)
                .withProperty("demo.secret", "ENC(abc)"));

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("demo.secret")).isEqualTo("ENC(abc)");
        assertThat(environment.getPropertySources().get("test-source")).isInstanceOf(MockPropertySource.class);
    }

    @Test
    @DisplayName("无密文配置：缺密钥也允许启动（本地开发不受影响）")
    void shouldSkipWithoutEncryptedValue() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MockPropertySource("test-source")
                .withProperty("demo.secret", "plain-value"));

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("demo.secret")).isEqualTo("plain-value");
    }
}
