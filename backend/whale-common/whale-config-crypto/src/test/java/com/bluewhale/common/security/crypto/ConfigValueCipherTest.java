package com.bluewhale.common.security.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ConfigValueCipher} 单测：密文往返、非密文透传与密钥校验。
 *
 * @author BlueWhale
 */
class ConfigValueCipherTest {

    /** 测试密钥（≥16 字符） */
    private static final String KEY = "blue-whale-test-key-0123456789";
    /** 另一个测试密钥，用于错密钥场景 */
    private static final String OTHER_KEY = "blue-whale-other-key-9876543210";

    @Test
    @DisplayName("encrypt/decrypt：ENC(...) 往返一致且每次密文不同（随机 IV）")
    void shouldRoundTripEncryptedValue() {
        ConfigValueCipher cipher = new ConfigValueCipher(ConfigEncryptionSettings.of(KEY));
        String first = cipher.encrypt("mysql-password");
        String second = cipher.encrypt("mysql-password");
        assertThat(first).startsWith("ENC(").endsWith(")").isNotEqualTo(second);
        assertThat(cipher.decryptIfNeeded("spring.datasource.password", first)).isEqualTo("mysql-password");
    }

    @Test
    @DisplayName("decryptIfNeeded：非 ENC 值原样返回，非字符串类型原样返回")
    void shouldKeepPlainValue() {
        ConfigValueCipher cipher = new ConfigValueCipher(ConfigEncryptionSettings.of(KEY));
        assertThat(cipher.decryptIfNeeded("server.port", "8081")).isEqualTo("8081");
        assertThat(cipher.decryptIfNeeded("server.port", 8081)).isEqualTo(8081);
        assertThat(cipher.decryptIfNeeded("empty", "ENC()")).isEqualTo("ENC()");
    }

    @Test
    @DisplayName("decrypt：错密钥解密失败且报错含属性名、不含密钥")
    void shouldFailWithWrongKey() {
        ConfigValueCipher encoder = new ConfigValueCipher(ConfigEncryptionSettings.of(KEY));
        ConfigValueCipher decoder = new ConfigValueCipher(ConfigEncryptionSettings.of(OTHER_KEY));
        String encrypted = encoder.encrypt("mysql-password");
        assertThatThrownBy(() -> decoder.decryptIfNeeded("spring.datasource.password", encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.password")
                .hasMessageNotContaining(KEY)
                .hasMessageNotContaining(OTHER_KEY);
    }

    @Test
    @DisplayName("密钥校验：缺失、过短、占位值均拒绝")
    void shouldRejectUnusableKey() {
        assertThatThrownBy(() -> ConfigEncryptionSettings.of(null).requireUsableKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ConfigEncryptionSettings.ENV_KEY);
        assertThatThrownBy(() -> ConfigEncryptionSettings.of("short").requireUsableKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("长度不足");
        assertThatThrownBy(() -> ConfigEncryptionSettings.of("change_me_user_pwd").requireUsableKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("占位值");
    }
}
