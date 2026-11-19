package com.bluewhale.message.support;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link CallbackUrlGuard} 回调地址 SSRF 防护测试。
 *
 * @author BlueWhale
 */
class CallbackUrlGuardTest {

    @Test
    @DisplayName("允许业务内网 http/https 回调，空地址视为未配置")
    void shouldAllowInternalCallback() {
        assertDoesNotThrow(() -> CallbackUrlGuard.assertAllowed("http://10.0.0.8:8080/message/callback"));
        assertDoesNotThrow(() -> CallbackUrlGuard.assertAllowed("https://biz.example.com/callback"));
        assertDoesNotThrow(() -> CallbackUrlGuard.assertAllowed(null));
        assertDoesNotThrow(() -> CallbackUrlGuard.assertAllowed("  "));
    }

    @Test
    @DisplayName("拒绝云元数据与链路本地地址")
    void shouldRejectMetadataAndLinkLocal() {
        assertThrows(BizException.class, () -> CallbackUrlGuard.assertAllowed("http://169.254.169.254/latest/meta-data"));
        assertThrows(BizException.class, () -> CallbackUrlGuard.assertAllowed("http://169.254.10.1/callback"));
        assertThrows(BizException.class, () -> CallbackUrlGuard.assertAllowed("http://100.100.100.200/latest/meta-data"));
    }

    @Test
    @DisplayName("拒绝非 http(s) 协议与携带用户信息的地址")
    void shouldRejectIllegalScheme() {
        assertThrows(BizException.class, () -> CallbackUrlGuard.assertAllowed("file:///etc/passwd"));
        assertThrows(BizException.class, () -> CallbackUrlGuard.assertAllowed("http://user:pass@biz.example.com/cb"));
    }
}
