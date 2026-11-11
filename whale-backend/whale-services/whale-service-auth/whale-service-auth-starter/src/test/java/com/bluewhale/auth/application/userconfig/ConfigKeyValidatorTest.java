package com.bluewhale.auth.application.userconfig;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConfigKeyValidator} 单测：键口径、前缀口径与 JSON 值校验。
 *
 * <p>正则校验必须配「可编译且语义正确」的单测——{@code @Pattern} 正则写错
 * 编译不报、运行期才炸（HV000032 教训），配置键校验同理。</p>
 *
 * @author BlueWhale
 */
class ConfigKeyValidatorTest {

    /**
     * 合法键应通过
     */
    @Test
    @DisplayName("合法的反域名点号键通过校验")
    void shouldAcceptValidKeys() {
        assertDoesNotThrow(() -> ConfigKeyValidator.requireValidKey("notify.twin.alert"));
        assertDoesNotThrow(() -> ConfigKeyValidator.requireValidKey("ui.theme"));
        assertDoesNotThrow(() -> ConfigKeyValidator.requireValidKey("notify.ops.alert.deep"));
    }

    /**
     * 非法键应抛 400
     */
    @Test
    @DisplayName("单段/大写/前导点号/空键抛参数异常")
    void shouldRejectInvalidKeys() {
        assertThrows(BizException.class, () -> ConfigKeyValidator.requireValidKey(null));
        assertThrows(BizException.class, () -> ConfigKeyValidator.requireValidKey(""));
        assertThrows(BizException.class, () -> ConfigKeyValidator.requireValidKey("theme"));
        assertThrows(BizException.class, () -> ConfigKeyValidator.requireValidKey("Notify.Alert"));
        assertThrows(BizException.class, () -> ConfigKeyValidator.requireValidKey(".notify.alert"));
        assertThrows(BizException.class, () -> ConfigKeyValidator.requireValidKey("notify..alert"));
        assertThrows(BizException.class, () -> ConfigKeyValidator.requireValidKey("notify.alert."));
    }

    /**
     * 超长键应抛 400
     */
    @Test
    @DisplayName("键超过 128 字符抛参数异常")
    void shouldRejectOverlongKey() {
        String key = "a.b".repeat(64) + ".x";
        assertTrue(key.length() > 128);
        assertThrows(BizException.class, () -> ConfigKeyValidator.requireValidKey(key));
    }

    /**
     * 前缀口径：空放行、小写点号放行、大写拒绝
     */
    @Test
    @DisplayName("前缀过滤参数空放行、小写点号放行、大写拒绝")
    void shouldValidatePrefix() {
        assertDoesNotThrow(() -> ConfigKeyValidator.requireValidPrefix(null));
        assertDoesNotThrow(() -> ConfigKeyValidator.requireValidPrefix(""));
        assertDoesNotThrow(() -> ConfigKeyValidator.requireValidPrefix("notify."));
        assertThrows(BizException.class, () -> ConfigKeyValidator.requireValidPrefix("Notify."));
        assertThrows(BizException.class, () -> ConfigKeyValidator.requireValidPrefix("notify alert"));
    }

    /**
     * 配置值：合法 JSON 放行，非法 JSON / 超长 / 空值拒绝
     */
    @Test
    @DisplayName("配置值必须是合法 JSON 且不超过大小上限")
    void shouldValidateValue() {
        assertDoesNotThrow(() -> ConfigKeyValidator.requireValidValue("{\"channels\":[\"EMAIL\"]}"));
        assertDoesNotThrow(() -> ConfigKeyValidator.requireValidValue("[]"));
        assertDoesNotThrow(() -> ConfigKeyValidator.requireValidValue("\"plain\""));
        assertThrows(BizException.class, () -> ConfigKeyValidator.requireValidValue(null));
        assertThrows(BizException.class, () -> ConfigKeyValidator.requireValidValue("  "));
        assertThrows(BizException.class, () -> ConfigKeyValidator.requireValidValue("{not-json"));
        assertThrows(BizException.class, () -> ConfigKeyValidator
                .requireValidValue("x".repeat(ConfigKeyValidator.MAX_VALUE_LENGTH + 1)));
    }
}
