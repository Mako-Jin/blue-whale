package com.bluewhale.tools.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 日期时间工具类
 * <p>统一 `java.time` 用法与常用格式，禁止在业务代码中散落格式字符串。</p>
 *
 * @author BlueWhale
 */
public final class DateUtils {

    /** 日期格式：yyyy-MM-dd */
    public static final String DATE_PATTERN = "yyyy-MM-dd";

    /** 日期时间格式：yyyy-MM-dd HH:mm:ss */
    public static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 日期格式化器（复用线程安全实例） */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_PATTERN);

    /** 日期时间格式化器（复用线程安全实例） */
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern(DATETIME_PATTERN);

    /**
     * 私有构造器，禁止实例化
     */
    private DateUtils() {
    }

    /**
     * 当前日期时间（系统默认时区）
     *
     * @return 当前时间
     */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /**
     * 当前日期（系统默认时区）
     *
     * @return 当前日期
     */
    public static LocalDate today() {
        return LocalDate.now();
    }

    /**
     * 格式化日期为 yyyy-MM-dd
     *
     * @param date 日期
     * @return 格式化结果
     */
    public static String formatDate(LocalDate date) {
        return DATE_FORMATTER.format(Objects.requireNonNull(date, "date 不能为空"));
    }

    /**
     * 格式化日期时间为 yyyy-MM-dd HH:mm:ss
     *
     * @param dateTime 日期时间
     * @return 格式化结果
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        return DATETIME_FORMATTER.format(Objects.requireNonNull(dateTime, "dateTime 不能为空"));
    }

    /**
     * 解析 yyyy-MM-dd 日期
     *
     * @param text 日期文本
     * @return 日期
     */
    public static LocalDate parseDate(String text) {
        return LocalDate.parse(requireText(text), DATE_FORMATTER);
    }

    /**
     * 解析 yyyy-MM-dd HH:mm:ss 日期时间
     *
     * @param text 日期时间文本
     * @return 日期时间
     */
    public static LocalDateTime parseDateTime(String text) {
        return LocalDateTime.parse(requireText(text), DATETIME_FORMATTER);
    }

    /**
     * 按自定义格式格式化日期时间
     *
     * @param dateTime 日期时间
     * @param pattern  格式，如 yyyyMMdd
     * @return 格式化结果
     */
    public static String format(LocalDateTime dateTime, String pattern) {
        return DateTimeFormatter.ofPattern(requireText(pattern))
                .format(Objects.requireNonNull(dateTime, "dateTime 不能为空"));
    }

    /**
     * 按自定义格式解析日期时间
     *
     * @param text    日期时间文本
     * @param pattern 格式
     * @return 日期时间
     */
    public static LocalDateTime parse(String text, String pattern) {
        return LocalDateTime.parse(requireText(text), DateTimeFormatter.ofPattern(requireText(pattern)));
    }

    /**
     * 取某日零点时刻
     *
     * @param date 日期
     * @return 当日 00:00:00
     */
    public static LocalDateTime startOfDay(LocalDate date) {
        return Objects.requireNonNull(date, "date 不能为空").atStartOfDay();
    }

    /**
     * 取某日最后一秒时刻（23:59:59）
     *
     * @param date 日期
     * @return 当日 23:59:59
     */
    public static LocalDateTime endOfDay(LocalDate date) {
        return startOfDay(date).plusDays(1).minusSeconds(1);
    }

    /**
     * 校验文本非空白（内部辅助）
     *
     * @param text 文本
     * @return 原文本
     */
    private static String requireText(String text) {
        AssertX.notBlank(text, "时间文本不能为空");
        return text;
    }
}
