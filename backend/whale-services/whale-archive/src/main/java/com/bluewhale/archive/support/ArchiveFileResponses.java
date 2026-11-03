package com.bluewhale.archive.support;

import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 档案文件响应支撑：MIME 解析、内联预览判定与 {@code Content-Disposition} 构造。
 *
 * <p>预览白名单与文件服务保持一致：{@code image/}、{@code video/}、{@code audio/}、
 * {@code text/} 前缀以及 PDF / JSON / XML 允许浏览器内联渲染；其余类型一律按附件下载，
 * 避免浏览器把可执行内容当页面解析。</p>
 *
 * @author BlueWhale
 */
public final class ArchiveFileResponses {

    /** 未知 MIME 类型的兜底值 */
    public static final String FALLBACK_CONTENT_TYPE = "application/octet-stream";

    /** 文件名兜底值 */
    public static final String FALLBACK_FILE_NAME = "archive-file";

    /** 允许内联渲染的 MIME 前缀 */
    private static final Set<String> INLINE_PREFIXES = Set.of("image/", "video/", "audio/", "text/");

    /** 允许内联渲染的完整 MIME */
    private static final Set<String> INLINE_TYPES = Set.of(
            "application/pdf", "application/json", "application/xml", "application/xhtml+xml");

    /** MIME 参数分隔符（如 {@code text/plain; charset=UTF-8}） */
    private static final char MIME_PARAM_SEPARATOR = ';';

    /** 非 ASCII 文件名的安全替换字符 */
    private static final char ASCII_PLACEHOLDER = '_';

    /** ASCII 可打印字符下界 */
    private static final int ASCII_PRINTABLE_MIN = 0x20;

    /** ASCII 可打印字符上界 */
    private static final int ASCII_PRINTABLE_MAX = 0x7E;

    /** 双引号字符：需在 ASCII 文件名中替换，避免破坏响应头 */
    private static final char QUOTE = '"';

    /**
     * 工具类禁止实例化。
     */
    private ArchiveFileResponses() {
    }

    /**
     * 解析 MIME，空值或非法值回落为二进制流。
     *
     * @param contentType 原始 MIME
     * @return 可用 MIME
     */
    public static MediaType mediaType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    /**
     * 判断该 MIME 是否允许浏览器内联预览 / 播放。
     *
     * @param contentType 原始 MIME
     * @return 允许内联时返回 {@code true}
     */
    public static boolean isInlinePreviewable(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return false;
        }
        String normalized = contentType.trim().toLowerCase();
        int separator = normalized.indexOf(MIME_PARAM_SEPARATOR);
        String base = separator < 0 ? normalized : normalized.substring(0, separator).trim();
        if (INLINE_TYPES.contains(base)) {
            return true;
        }
        return INLINE_PREFIXES.stream().anyMatch(base::startsWith);
    }

    /**
     * 构造 {@code Content-Disposition}：ASCII 兜底文件名 + RFC 5987 编码文件名。
     *
     * @param fileName 原始文件名
     * @param inline 是否内联展示（预览 / 播放）而非附件下载
     * @return Content-Disposition 值
     */
    public static String contentDisposition(String fileName, boolean inline) {
        String name = StringUtils.hasText(fileName) ? fileName : FALLBACK_FILE_NAME;
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        StringBuilder ascii = new StringBuilder(name.length());
        for (char ch : name.toCharArray()) {
            ascii.append(ch < ASCII_PRINTABLE_MIN || ch > ASCII_PRINTABLE_MAX || ch == QUOTE
                    ? ASCII_PLACEHOLDER : ch);
        }
        String mode = inline ? "inline" : "attachment";
        return mode + "; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }
}
