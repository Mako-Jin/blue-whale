package com.bluewhale.pm.domain.service;

import java.util.Locale;

/**
 * 工作项排序值策略：12 位定长数字字符串，步长 1000，支持相邻项之间取中值。
 *
 * <p>空间不足（相邻值差 ≤ 1）时返回 {@code null}，由应用服务触发项目内局部重排。</p>
 *
 * @author BlueWhale
 */
public final class RankPolicy {

    /** 排序步长 */
    private static final long STEP = 1000L;

    /** 排序值格式：12 位左补零 */
    private static final String FORMAT = "%012d";

    /** 解析后的最大值下限 */
    private static final long MIN_GAP = 1L;

    /**
     * 工具类不允许实例化
     */
    private RankPolicy() {
    }

    /**
     * 首个排序值（1000）。
     *
     * @return 排序值
     */
    public static String initial() {
        return format(STEP);
    }

    /**
     * 解析排序值为数字，非法或空值按 0 处理。
     *
     * @param rank 排序值
     * @return 数字值
     */
    public static long parse(String rank) {
        if (rank == null || rank.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(rank);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 追加到当前最大排序值之后。
     *
     * @param maxRank 当前最大排序值，可为 {@code null}
     * @return 新排序值
     */
    public static String nextAfter(String maxRank) {
        return format(parse(maxRank) + STEP);
    }

    /**
     * 计算位于两个排序值之间的新值。
     *
     * @param lower 下界（不含端点的排序值），可为 {@code null} 表示列表首位
     * @param upper 上界，可为 {@code null} 表示列表末位
     * @return 新排序值；下界与上界之间无空间时返回 {@code null}
     */
    public static String between(Long lower, Long upper) {
        long lo = lower == null ? 0L : lower;
        if (upper == null) {
            return format(lo + STEP);
        }
        if (upper - lo <= MIN_GAP) {
            return null;
        }
        return format((lo + upper) / 2);
    }

    /**
     * 格式化为 12 位左补零字符串。
     *
     * @param value 数字值
     * @return 排序值
     */
    public static String format(long value) {
        return String.format(Locale.ROOT, FORMAT, value);
    }
}
