package com.bluewhale.common.security.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@code META-INF/spring.factories} 在 Spring Boot 4 下仍能装载
 * {@link ConfigEncryptionEnvironmentPostProcessor}，并完成 {@code ENC(...)} 解密。
 *
 * @author BlueWhale
 */
class ConfigEncryptionRegistrationTest {

    /** 测试密钥（≥16 字符） */
    private static final String KEY = "blue-whale-test-key-0123456789";

    @Test
    @DisplayName("SpringApplication 启动：spring.factories 注册的 EPP 生效，ENC(...) 属性被解密")
    void shouldDecryptThroughRegisteredPostProcessor() {
        String encrypted = new ConfigValueCipher(ConfigEncryptionSettings.of(KEY)).encrypt("decrypted-value");
        SpringApplication application = new SpringApplication(TestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext context = application.run(
                "--spring.main.banner-mode=off",
                "--spring.main.log-startup-info=false",
                "--whale.config.encryption.enabled=true",
                "--whale.config.encryption.password=" + KEY,
                "--whale.test.secret=" + encrypted)) {
            assertThat(context.getEnvironment().getProperty("whale.test.secret")).isEqualTo("decrypted-value");
            assertThat(context.getEnvironment().getPropertySources().get("commandLineArgs"))
                    .isInstanceOfAny(EncryptedPropertySource.class, EncryptedEnumerablePropertySource.class);
        }
    }

    /**
     * 最小 Spring Boot 应用（无任何 Bean，仅用于触发环境准备流程）
     */
    @SpringBootApplication
    static class TestApplication {
    }
}
