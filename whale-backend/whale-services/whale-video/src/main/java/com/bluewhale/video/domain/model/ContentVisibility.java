package com.bluewhale.video.domain.model;

/**
 * 可见性：公开（租户内可见）/ 私密（仅上传者可见可播）。
 *
 * @author BlueWhale
 */
public enum ContentVisibility {

    /** 公开：租户内所有用户可见 */
    PUBLIC,

    /** 私密：仅上传者本人可见可播 */
    PRIVATE;

    /**
     * 解析可见性文本。
     *
     * @param value 存储 / 请求文本
     * @return 可见性；无法识别回落 {@link #PUBLIC}
     */
    public static ContentVisibility parse(String value) {
        for (ContentVisibility item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return PUBLIC;
    }
}
