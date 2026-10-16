package com.bluewhale.tools.utils;

import cn.hutool.core.util.StrUtil;

/**
 * 通用断言工具类，断言失败统一抛出 {@link IllegalArgumentException}
 *
 * @author BlueWhale
 */
public final class AssertX {

    private AssertX() {
    }

    /**
     * 断言表达式为 true
     */
    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言对象不为 null
     */
    public static void notNull(Object object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言对象为 null
     */
    public static void isNull(Object object, String message) {
        if (object != null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言字符串非空白（null、空串、纯空白均视为空白）
     */
    public static void notBlank(String text, String message) {
        if (StrUtil.isBlank(text)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言字符串为空白（notBlank 的反向断言）
     */
    public static void isBlank(String text, String message) {
        if (StrUtil.isNotBlank(text)) {
            throw new IllegalArgumentException(message);
        }
    }
}
