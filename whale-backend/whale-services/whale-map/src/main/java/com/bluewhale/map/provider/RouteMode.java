package com.bluewhale.map.provider;

/**
 * 出行方式。
 *
 * @author BlueWhale
 */
public enum RouteMode {

    /** 驾车 */
    DRIVING,

    /** 步行 */
    WALKING,

    /** 骑行 */
    CYCLING;

    /**
     * 宽松解析出行方式
     *
     * @param text 出行方式文本
     * @return 出行方式；无法识别返回 {@code null}
     */
    public static RouteMode parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
