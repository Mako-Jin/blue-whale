package com.bluewhale.lms.training.domain.model;

/**
 * 培训班形式。
 *
 * @author BlueWhale
 */
public enum TrainingMode {

    /** 线上（依赖关联课程与课件） */
    ONLINE,

    /** 面授（依赖场次与签到） */
    OFFLINE,

    /** 线上 + 面授混合 */
    BLENDED;

    /**
     * 解析形式，未知取值回退面授。
     *
     * @param raw 原始取值
     * @return 培训班形式
     */
    public static TrainingMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return OFFLINE;
        }
        for (TrainingMode mode : values()) {
            if (mode.name().equalsIgnoreCase(raw.trim())) {
                return mode;
            }
        }
        return OFFLINE;
    }
}
