package com.bluewhale.agent.support.ingest;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 切分公共工具：按字符上限切窗、模块识别、Wave 标记提取。
 *
 * @author BlueWhale
 */
public final class ChunkSupport {

    /** Wave 标记（如 Wave 5.47） */
    private static final Pattern WAVE_PATTERN = Pattern.compile("Wave\\s*([0-9]+(?:\\.[0-9]+)*)");

    /** 单块正文上限（字符） */
    public static final int MAX_CHARS = 2400;

    /** 窗口重叠行数 */
    public static final int OVERLAP_LINES = 4;

    /** 工具类不允许实例化 */
    private ChunkSupport() {
    }

    /**
     * 按字符上限把行集合切成带重叠的窗口。
     *
     * @param lines 源行（不含换行符）
     * @param maxChars 单窗口字符上限
     * @param overlapLines 相邻窗口重叠行数
     * @return 窗口列表；入参为空时返回空列表
     */
    public static List<Window> splitWindows(List<String> lines, int maxChars, int overlapLines) {
        List<Window> windows = new ArrayList<>();
        if (lines == null || lines.isEmpty()) {
            return windows;
        }
        int start = 0;
        while (start < lines.size()) {
            int end = start;
            int chars = 0;
            while (end < lines.size()) {
                int next = lines.get(end).length() + 1;
                if (chars + next > maxChars && end > start) {
                    break;
                }
                chars += next;
                end++;
            }
            windows.add(new Window(join(lines, start, end), start + 1, end));
            if (end >= lines.size()) {
                break;
            }
            start = Math.max(end - overlapLines, start + 1);
        }
        return windows;
    }

    /**
     * 拼接指定行区间。
     *
     * @param lines 源行
     * @param from 起始下标（含）
     * @param to 结束下标（不含）
     * @return 以换行拼接的文本
     */
    public static String join(List<String> lines, int from, int to) {
        return String.join("\n", lines.subList(from, to));
    }

    /**
     * 从文本中提取首个 Wave 标记。
     *
     * @param text 文本
     * @return Wave 号（如 5.47）；无匹配返回空串
     */
    public static String extractWave(String text) {
        if (text == null) {
            return "";
        }
        Matcher matcher = WAVE_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * 从仓库相对路径识别所属模块（匹配 {@code whale-*} 目录段）。
     *
     * @param path 仓库相对路径（/ 分隔）
     * @return 模块名；无法识别返回空串
     */
    public static String moduleOf(String path) {
        if (path == null) {
            return "";
        }
        for (String segment : path.split("/")) {
            if (segment.startsWith("whale-") && !segment.contains(".")) {
                return segment;
            }
        }
        return "";
    }

    /**
     * 窗口结果：文本 + 行号区间。
     *
     * @author BlueWhale
     */
    @Getter
    @AllArgsConstructor
    public static class Window {

        /** 窗口文本 */
        private final String text;

        /** 起始行号（1 起） */
        private final int startLine;

        /** 结束行号（1 起，含） */
        private final int endLine;
    }
}
