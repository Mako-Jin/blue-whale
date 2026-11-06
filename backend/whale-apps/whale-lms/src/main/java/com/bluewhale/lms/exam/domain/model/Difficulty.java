package com.bluewhale.lms.exam.domain.model;

import java.util.Locale;

/**
 * 题目难度。
 *
 * @author BlueWhale
 */
public enum Difficulty {

    /** 简单 */
    EASY,

    /** 一般 */
    NORMAL,

    /** 困难 */
    HARD;

    /**
     * 解析难度（空值按一般处理）。
     *
     * @param raw 原始值
     * @return 难度
     */
    public static Difficulty parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NORMAL;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (Difficulty difficulty : values()) {
            if (difficulty.name().equals(normalized)) {
                return difficulty;
            }
        }
        return NORMAL;
    }
}
