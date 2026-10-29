package com.bluewhale.doc.support;

import com.bluewhale.doc.config.DocProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

/**
 * 页面正文处理：纯文本投影、体积校验与内容哈希。
 *
 * <p>TipTap 文本节点形如 {@code {"type":"text","text":"..."}}；服务端只信任 JSON，
 * 不接受前端提交的 {@code contentText}。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class DocContentSupport {

    private static final Set<String> NEWLINE_BLOCKS =
            Set.of("paragraph", "heading", "blockquote", "listItem", "codeBlock");

    private final ObjectMapper objectMapper;
    private final DocProperties docProperties;

    /**
     * 校验正文 JSON 体积。
     *
     * @param contentJson 正文 JSON
     * @throws com.bluewhale.common.core.exception.BizException 超过上限时抛出 40001
     */
    public void requireWithinLimit(String contentJson) {
        if (contentJson == null) {
            return;
        }
        long bytes = contentJson.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > docProperties.getContentMaxBytes()) {
            throw com.bluewhale.doc.domain.exception.DocErrorCode.exception(
                    com.bluewhale.doc.domain.exception.DocErrorCode.CONTENT_TOO_LARGE);
        }
    }

    /**
     * 从正文 JSON 提取纯文本投影。
     *
     * @param contentJson 正文 JSON
     * @return 归一化并截断后的纯文本
     */
    public String extractText(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try {
            walk(objectMapper.readTree(contentJson), builder);
        } catch (RuntimeException ex) {
            return "";
        }
        String normalized = builder.toString()
                .replaceAll("[\\t\\r\\f\\u000B]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return truncateBytes(normalized, docProperties.getContentTextMaxBytes());
    }

    /**
     * 计算正文 SHA-256。
     *
     * @param contentJson 正文 JSON
     * @return 十六进制哈希
     */
    public String sha256(String contentJson) {
        if (contentJson == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(contentJson.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    /**
     * 递归遍历 TipTap JSON 节点，拼接文本并补齐块级换行。
     *
     * @param node    当前节点
     * @param builder 文本缓冲
     */
    private void walk(JsonNode node, StringBuilder builder) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                walk(child, builder);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        String type = node.path("type").asText("");
        if ("text".equals(type)) {
            builder.append(node.path("text").asText(""));
            return;
        }
        if ("hardBreak".equals(type)) {
            builder.append('\n');
            return;
        }
        JsonNode content = node.path("content");
        if (!content.isArray()) {
            return;
        }
        boolean newline = NEWLINE_BLOCKS.contains(type);
        if (newline) {
            builder.append('\n');
        }
        walk(content, builder);
        if (newline) {
            builder.append('\n');
        }
    }

    /**
     * 按字节上限截断文本，并避免切断多字节字符。
     *
     * @param text     原始文本
     * @param maxBytes 最大字节数
     * @return 截断后的文本
     */
    private String truncateBytes(String text, long maxBytes) {
        if (maxBytes <= 0) {
            return "";
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return text;
        }
        int end = (int) Math.min(bytes.length, maxBytes);
        while (end > 0 && (bytes[end - 1] & 0x80) != 0) {
            end--;
        }
        return new String(bytes, 0, end, StandardCharsets.UTF_8);
    }
}
