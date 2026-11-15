package com.bluewhale.file.service.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link CdnUrlRewriter} 单元测试：主机替换与安全回退。
 *
 * @author BlueWhale
 */
class CdnUrlRewriterTest {

    private static final String OBJECT_URL =
            "http://127.0.0.1:9000/bluewhale/2026/10/24/a.txt?X-Amz-Signature=abc";

    @Test
    @DisplayName("仅替换主机，路径与签名查询串保持不变")
    void shouldReplaceAuthorityOnly() {
        String rewritten = CdnUrlRewriter.rewrite(OBJECT_URL, "https://cdn.example.com");

        assertEquals("https://cdn.example.com/bluewhale/2026/10/24/a.txt?X-Amz-Signature=abc", rewritten);
    }

    @Test
    @DisplayName("CDN 地址结尾带斜杠时不产生双斜杠")
    void shouldTrimTrailingSlashOfBaseUrl() {
        String rewritten = CdnUrlRewriter.rewrite(OBJECT_URL, "https://cdn.example.com/");

        assertEquals("https://cdn.example.com/bluewhale/2026/10/24/a.txt?X-Amz-Signature=abc", rewritten);
    }

    @Test
    @DisplayName("CDN 地址为空白时原样返回")
    void shouldKeepUrlWhenBaseUrlBlank() {
        assertEquals(OBJECT_URL, CdnUrlRewriter.rewrite(OBJECT_URL, ""));
        assertEquals(OBJECT_URL, CdnUrlRewriter.rewrite(OBJECT_URL, "   "));
        assertEquals(OBJECT_URL, CdnUrlRewriter.rewrite(OBJECT_URL, null));
    }

    @Test
    @DisplayName("URL 为空或是相对地址时原样返回")
    void shouldKeepUnsupportedUrl() {
        assertNull(CdnUrlRewriter.rewrite(null, "https://cdn.example.com"));
        assertEquals("/bluewhale/a.txt", CdnUrlRewriter.rewrite("/bluewhale/a.txt", "https://cdn.example.com"));
    }

    @Test
    @DisplayName("URL 没有路径部分时返回 CDN 地址本身")
    void shouldReturnBaseWhenUrlHasNoPath() {
        assertEquals("https://cdn.example.com", CdnUrlRewriter.rewrite("http://127.0.0.1:9000",
                "https://cdn.example.com"));
    }
}
