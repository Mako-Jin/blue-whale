package com.bluewhale.video.domain.model;

/**
 * 标签类型：主题 / 类型 / 语言。
 *
 * @author BlueWhale
 */
public enum TagType {

    /** 主题标签 */
    TOPIC,

    /** 类型标签 */
    GENRE,

    /** 语言标签 */
    LANGUAGE;

    /**
     * 解析标签类型文本。
     *
     * @param value 存储 / 请求文本
     * @return 标签类型；无法识别回落 {@link #TOPIC}
     */
    public static TagType parse(String value) {
        for (TagType item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return TOPIC;
    }
}
