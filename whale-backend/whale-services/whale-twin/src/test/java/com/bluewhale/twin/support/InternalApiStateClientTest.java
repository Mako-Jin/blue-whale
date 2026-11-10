package com.bluewhale.twin.support;

import com.bluewhale.twin.config.TwinProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * internal_api 绑定源客户端纯逻辑单测：路径规范化、白名单与 base-url 路由
 * （不发真实 HTTP；信封解包与失败策略由运行时单测覆盖）。
 *
 * @author BlueWhale
 */
class InternalApiStateClientTest {

    /**
     * 构造客户端（可定制配置）。
     *
     * @param customizer 配置定制
     * @return 客户端
     */
    private static InternalApiStateClient client(java.util.function.Consumer<TwinProperties> customizer) {
        TwinProperties properties = new TwinProperties();
        customizer.accept(properties);
        return new InternalApiStateClient(properties);
    }

    @Test
    void normalizePathAcceptsLeadingSlashAndRejectsTraversal() {
        InternalApiStateClient client = client(properties -> { });

        assertEquals("/iot/internal/device/state?deviceId=1",
                client.normalizePath("/iot/internal/device/state?deviceId=1"));
        assertNull(client.normalizePath("iot/internal/device/state"));
        assertNull(client.normalizePath("/iot/internal/../admin/users"));
        assertNull(client.normalizePath("/iot/internal/device/state?url=http://evil.com"));
        assertNull(client.normalizePath(null));
    }

    @Test
    void isAllowedFollowsWhitelistPrefixes() {
        InternalApiStateClient client = client(properties -> { });

        assertTrue(client.isAllowed("/iot/internal/device/state"));
        assertTrue(client.isAllowed("/twin/internal/scene/overview"));
        assertTrue(client.isAllowed("/assistant/openapi/v1/chat"));
        assertFalse(client.isAllowed("/system/admin/users"));
        assertFalse(client.isAllowed("/iot/public/device/state"));
    }

    @Test
    void isAllowedReturnsFalseWhenDisabled() {
        InternalApiStateClient client =
                client(properties -> properties.getInternalApi().setEnabled(false));

        assertFalse(client.isAllowed("/iot/internal/device/state"));
    }

    @Test
    void resolveBaseUrlRoutesIotByFallback() {
        InternalApiStateClient client = client(properties -> { });

        assertEquals("http://127.0.0.1:8088",
                client.resolveBaseUrl("/iot/internal/device/state"));
    }

    @Test
    void resolveBaseUrlPrefersRoutingTable() {
        InternalApiStateClient client = client(properties -> properties.getInternalApi()
                .getBaseUrls().put("iot", "http://127.0.0.1:9099/"));

        assertEquals("http://127.0.0.1:9099",
                client.resolveBaseUrl("/iot/internal/device/state"));
    }

    @Test
    void resolveBaseUrlRejectsServiceWithoutRoute() {
        InternalApiStateClient client = client(properties -> { });

        assertNull(client.resolveBaseUrl("/twin/internal/scene/overview"));
    }

    @Test
    void resolveBaseUrlRejectsPathOutsideWhitelist() {
        InternalApiStateClient client = client(properties -> properties.getInternalApi()
                .getBaseUrls().put("system", "http://127.0.0.1:9000"));

        assertNull(client.resolveBaseUrl("/system/admin/users"));
    }
}
