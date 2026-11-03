package com.bluewhale.video.domain.model;

/**
 * 内容类型：短视频 / 视频 / 电影。
 *
 * <p>三类内容共享 {@code vd_content} 注册表，子类型表以 {@code content_id} 1:1 扩展。</p>
 *
 * @author BlueWhale
 */
public enum ContentType {

    /** 短视频（9:16 为主，限时长与体积） */
    SHORT_VIDEO,

    /** 普通视频（可入系列） */
    VIDEO,

    /** 电影（仅运营入库，授权到期自动下线） */
    MOVIE;

    /**
     * 解析内容类型文本。
     *
     * @param value 存储 / 请求文本
     * @return 内容类型；无法识别返回 {@code null}
     */
    public static ContentType parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (ContentType item : values()) {
            if (item.name().equalsIgnoreCase(value.trim())) {
                return item;
            }
        }
        return null;
    }
}
