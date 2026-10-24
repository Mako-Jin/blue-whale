package com.bluewhale.tools.utils;

import cn.hutool.core.util.StrUtil;

/**
 * 字符串工具类
 * <p>统一委托 hutool {@link StrUtil}，避免各服务自行实现空白/空串判定导致边界不一致。</p>
 *
 * @author BlueWhale
 */
public final class StringUtils {

    /**
     * 私有构造器，禁止实例化
     */
    private StringUtils() {
    }

    /**
     * 判断字符串是否为空白：null、空串、纯空白字符均视为空白
     *
     * @param text 待判定字符串
     * @return 空白返回 true
     */
    public static boolean isBlank(CharSequence text) {
        return StrUtil.isBlank(text);
    }

    /**
     * 判断字符串是否非空白（{@link #isBlank(CharSequence)} 的反向判定）
     *
     * @param text 待判定字符串
     * @return 非空白返回 true
     */
    public static boolean isNotBlank(CharSequence text) {
        return StrUtil.isNotBlank(text);
    }

    /**
     * 判断字符串是否为空：null 或长度为 0（纯空白不算空）
     *
     * @param text 待判定字符串
     * @return 空返回 true
     */
    public static boolean isEmpty(CharSequence text) {
        return StrUtil.isEmpty(text);
    }

    /**
     * 判断字符串是否非空（{@link #isEmpty(CharSequence)} 的反向判定）
     *
     * @param text 待判定字符串
     * @return 非空返回 true
     */
    public static boolean isNotEmpty(CharSequence text) {
        return StrUtil.isNotEmpty(text);
    }

    /**
     * 字符串为空白时返回默认值，否则原样返回
     *
     * @param text         原字符串
     * @param defaultValue 默认值
     * @return 原字符串或默认值
     */
    public static String defaultIfBlank(String text, String defaultValue) {
        return StrUtil.isBlank(text) ? defaultValue : text;
    }

    /**
     * 去除首尾空白；结果为空白时返回 null
     *
     * @param text 原字符串
     * @return 去空白后的字符串或 null
     */
    public static String trimToNull(String text) {
        return StrUtil.trimToNull(text);
    }
}
