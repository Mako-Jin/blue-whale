package com.bluewhale.twin.support;

import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * 告警静默时段判定（Wave 6.11 M1）：纯函数，无状态、可直接单测。
 *
 * <p>窗口用 {@code HH:mm}–{@code HH:mm} 表达，<b>支持跨零点</b>（如 22:00–08:00 表示
 * 「晚十点到次日早八点」），因此不能简单写成 {@code now >= start && now < end}。</p>
 *
 * <p>几个口径：</p>
 * <ul>
 *   <li>起止任一为空/非法 → 视为未配置，<b>fail-open</b>（配置写错不该把告警全静音掉）；</li>
 *   <li>起止相同 → 视为未配置（既是零长度窗口，也避免「24 小时静默」的误配）；</li>
 *   <li>窗口左闭右开：{@code [start, end)}，22:00 静默、08:00 恢复。</li>
 * </ul>
 *
 * @author BlueWhale
 */
public final class TwinQuietHours {

    /** 合法时刻格式：一位或两位小时 + 两位分钟 */
    private static final Pattern HHMM = Pattern.compile("^([01]?\\d|2[0-3]):[0-5]\\d$");

    /** 输出统一补零到两位小时 */
    private static final DateTimeFormatter OUTPUT = DateTimeFormatter.ofPattern("HH:mm");

    /** 工具类：禁止实例化 */
    private TwinQuietHours() {
    }

    /**
     * 归一化时刻字符串（非法返回 {@code null}）。
     *
     * @param raw 原始输入（如 {@code 9:05}、{@code 09:05}）
     * @return 归一化后的 {@code HH:mm}；非法或空返回 {@code null}
     */
    public static String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        if (!HHMM.matcher(trimmed).matches()) {
            return null;
        }
        int colon = trimmed.indexOf(':');
        int hour = Integer.parseInt(trimmed.substring(0, colon));
        int minute = Integer.parseInt(trimmed.substring(colon + 1));
        return OUTPUT.format(LocalTime.of(hour, minute));
    }

    /**
     * 判断给定时刻是否落在静默窗口内。
     *
     * @param start 窗口开始（{@code HH:mm}，可空）
     * @param end   窗口结束（{@code HH:mm}，可空）
     * @param now   当前时刻
     * @return 处于静默返回 {@code true}
     */
    public static boolean active(String start, String end, LocalTime now) {
        LocalTime from = parse(start);
        LocalTime to = parse(end);
        if (from == null || to == null || now == null || from.equals(to)) {
            return false;
        }
        if (from.isBefore(to)) {
            // 同日窗口：[from, to)
            return !now.isBefore(from) && now.isBefore(to);
        }
        // 跨零点窗口：[from, 24:00) ∪ [00:00, to)
        return !now.isBefore(from) || now.isBefore(to);
    }

    /**
     * 两个窗口是否「有交叠」——仅供展示提示，判定一律用 {@link #active}。
     *
     * @param leftStart  左窗口开始
     * @param leftEnd    左窗口结束
     * @param rightStart 右窗口开始
     * @param rightEnd   右窗口结束
     * @return 任一时刻可能同时命中两个窗口时返回 {@code true}
     */
    public static boolean overlaps(String leftStart, String leftEnd,
                                   String rightStart, String rightEnd) {
        LocalTime a1 = parse(leftStart);
        LocalTime a2 = parse(leftEnd);
        LocalTime b1 = parse(rightStart);
        LocalTime b2 = parse(rightEnd);
        if (a1 == null || a2 == null || b1 == null || b2 == null) {
            return false;
        }
        for (int minute = 0; minute < 24 * 60; minute++) {
            LocalTime probe = LocalTime.of(minute / 60, minute % 60);
            if (active(leftStart, leftEnd, probe) && active(rightStart, rightEnd, probe)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析单个时刻（非法返回 {@code null}）。
     *
     * @param raw 原始时刻
     * @return 时刻或 {@code null}
     */
    private static LocalTime parse(String raw) {
        String normalized = normalize(raw);
        return normalized == null ? null : LocalTime.parse(normalized);
    }
}
