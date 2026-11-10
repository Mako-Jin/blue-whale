package com.bluewhale.video.domain.model;

/**
 * 内容来源声明（版权合规留痕）。
 *
 * <p>上传时必填，与上传声明签署记录中的来源一致；为空视为未声明。</p>
 *
 * @author BlueWhale
 */
public enum CopyrightSource {

    /** 原创 */
    ORIGINAL,

    /** 已获得授权 */
    AUTHORIZED,

    /** 无版权争议素材 */
    ROYALTY_FREE;

    /**
     * 解析内容来源文本。
     *
     * @param value 存储 / 请求文本
     * @return 内容来源；无法识别返回 {@code null}
     */
    public static CopyrightSource parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (CopyrightSource item : values()) {
            if (item.name().equalsIgnoreCase(value.trim())) {
                return item;
            }
        }
        return null;
    }
}
