package com.bluewhale.lms.course.domain.model;

import java.util.Arrays;

/**
 * 课件类型。
 *
 * <p>课件字节归 {@code whale-file}，LMS 只保存 {@code fileRefId} 引用；
 * 外链类型（LINK）仅保存 URL，不占用文件存储。</p>
 *
 * @author BlueWhale
 */
public enum WareType {

    /** 文档 */
    DOCUMENT,

    /** 视频（字节归 whale-file，播放能力不在 LMS） */
    VIDEO,

    /** 外部链接 */
    LINK;

    /**
     * 是否必须携带文件引用。
     *
     * @return DOCUMENT / VIDEO 返回 true
     */
    public boolean requiresFileRef() {
        return this != LINK;
    }

    /**
     * 解析类型文本。
     *
     * @param value 类型文本
     * @return 类型枚举
     */
    public static WareType of(String value) {
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("课件类型非法：" + value));
    }
}
