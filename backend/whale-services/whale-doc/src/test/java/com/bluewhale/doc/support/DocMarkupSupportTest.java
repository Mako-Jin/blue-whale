package com.bluewhale.doc.support;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DocMarkupSupport} 导出安全断言。
 *
 * @author BlueWhale
 */
class DocMarkupSupportTest {

    private final DocMarkupSupport support = new DocMarkupSupport(new ObjectMapper());

    @Test
    void htmlExportShouldEscapeScriptText() {
        String html = support.toHtml(content("<script>alert('xss')</script>"));
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    @Test
    void htmlExportShouldDropExecutableLinkProtocol() {
        String html = support.toHtml(content("click", "link", "javascript:alert(1)"));
        assertFalse(html.contains("javascript:"));
        assertTrue(html.contains("click"));
    }

    @Test
    void htmlExportShouldKeepHttpLinkWithNoopener() {
        String html = support.toHtml(content("site", "link", "https://example.com"));
        assertTrue(html.contains("href=\"https://example.com\""));
        assertTrue(html.contains("rel=\"noopener noreferrer\""));
    }

    @Test
    void markdownExportShouldDropExecutableLinkProtocol() {
        String markdown = support.toMarkdown(content("click", "link", "javascript:alert(1)"));
        assertFalse(markdown.contains("javascript:"));
        assertTrue(markdown.contains("click"));
    }

    /**
     * 构造带单个文本节点的 TipTap JSON。
     *
     * @param text 文本
     * @return 正文 JSON
     */
    private String content(String text) {
        return content(text, null, null);
    }

    /**
     * 构造可附带 link mark 的 TipTap JSON。
     *
     * @param text 文本
     * @param mark 标记类型
     * @param href 链接地址
     * @return 正文 JSON
     */
    private String content(String text, String mark, String href) {
        StringBuilder json = new StringBuilder("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"")
                .append(text.replace("\"", "\\\""))
                .append("\"");
        if (mark != null) {
            json.append(",\"marks\":[{\"type\":\"")
                    .append(mark)
                    .append("\",\"attrs\":{\"href\":\"")
                    .append(href)
                    .append("\"}}]");
        }
        json.append("}]}]}");
        return json.toString();
    }
}
