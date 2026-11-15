package com.bluewhale.file.service.support;

import org.springframework.util.StringUtils;

/**
 * CDN 地址重写工具：把预签名 URL 的 {@code scheme://host[:port]} 替换为 CDN 地址。
 *
 * <p>只做前缀替换，路径与查询串原样拼接：S3/OSS 预签名不把 host 纳入签名，替换域名后仍可校验；
 * 重新解析并序列化 URL 会改动查询串编码，反而可能破坏签名，因此这里按字符串处理。</p>
 *
 * <p>无法安全改写时一律返回原值：空 URL、空 CDN 地址、不含 {@code ://} 的相对地址。</p>
 *
 * @author BlueWhale
 */
public final class CdnUrlRewriter {

    /** scheme 与 authority 之间的分隔符 */
    private static final String SCHEME_SEPARATOR = "://";

    /**
     * 私有构造：工具类不允许实例化
     */
    private CdnUrlRewriter() {
    }

    /**
     * 把绝对 URL 的主机部分替换为 CDN 地址
     *
     * @param url 原始 URL，可为 {@code null}
     * @param baseUrl CDN 地址（含 scheme），可为 {@code null} 或空白
     * @return 重写后的 URL；无需或无法重写时返回原值
     */
    public static String rewrite(String url, String baseUrl) {
        if (!StringUtils.hasText(url) || !StringUtils.hasText(baseUrl) || !url.contains(SCHEME_SEPARATOR)) {
            return url;
        }
        String base = trimTrailingSlash(baseUrl.trim());
        int authorityEnd = url.indexOf('/', url.indexOf(SCHEME_SEPARATOR) + SCHEME_SEPARATOR.length());
        return authorityEnd < 0 ? base : base + url.substring(authorityEnd);
    }

    /**
     * 去掉 CDN 地址结尾的斜杠，避免与对象路径拼接后出现双斜杠
     *
     * @param baseUrl CDN 地址
     * @return 去掉结尾斜杠的地址
     */
    private static String trimTrailingSlash(String baseUrl) {
        int end = baseUrl.length();
        while (end > 0 && baseUrl.charAt(end - 1) == '/') {
            end--;
        }
        return baseUrl.substring(0, end);
    }
}
