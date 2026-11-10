package com.bluewhale.doc.support;

import java.util.Locale;

/**
 * 页面 slug 处理。
 *
 * @author BlueWhale
 */
public final class DocSlugs {

    private static final int MAX_LENGTH = 128;
    private static final String FALLBACK_PREFIX = "p-";

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private DocSlugs() {
    }

    /**
     * 规范化 slug；非法或空白时回退为 {@code p-{id}}。
     *
     * @param raw 原始 slug
     * @param id  页面ID
     * @return 规范化后的 slug
     */
    public static String normalize(String raw, Long id) {
        if (raw == null || raw.isBlank()) {
            return FALLBACK_PREFIX + id;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9_-]+")) {
            return FALLBACK_PREFIX + id;
        }
        return value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) : value;
    }

    /**
     * 生成删除后的墓碑 slug，避免唯一键冲突。
     *
     * @param slug 原 slug
     * @param id   页面ID
     * @return 墓碑 slug
     */
    public static String trashSlug(String slug, Long id) {
        String suffix = "-trash-" + id;
        String base = slug == null ? "" : slug;
        int maxBase = MAX_LENGTH - suffix.length();
        if (maxBase < 1) {
            return suffix.substring(0, MAX_LENGTH);
        }
        return (base.length() > maxBase ? base.substring(0, maxBase) : base) + suffix;
    }

    /**
     * 从回收站 slug 还原原始 slug 基座（去除 {@code -trash-{id}} 后缀）。
     *
     * @param slug 回收站中的 slug
     * @param id   页面ID
     * @return 原始 slug；无法识别后缀时原样返回
     */
    public static String restoreBase(String slug, Long id) {
        if (slug == null || slug.isBlank()) {
            return FALLBACK_PREFIX + id;
        }
        String suffix = "-trash-" + id;
        if (!slug.endsWith(suffix) || slug.length() <= suffix.length()) {
            return slug;
        }
        String base = slug.substring(0, slug.length() - suffix.length());
        return base.isBlank() ? FALLBACK_PREFIX + id : base;
    }
}
