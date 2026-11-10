package com.bluewhale.video.domain.model;

/**
 * 版权状态：有效 / 版权下架。
 *
 * <p>版权下架优先级最高：即使内容处于已发布状态也禁止签发播放票据，
 * 并阻断后续每一次 Range 流式请求。</p>
 *
 * @author BlueWhale
 */
public enum CopyrightState {

    /** 版权有效 */
    AUTHORIZED,

    /** 版权下架（须填写原因并留痕） */
    TAKEDOWN;

    /**
     * 解析版权状态文本。
     *
     * @param value 存储文本
     * @return 版权状态；无法识别回落 {@link #AUTHORIZED}
     */
    public static CopyrightState parse(String value) {
        for (CopyrightState item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return AUTHORIZED;
    }

    /**
     * 当前版权状态是否允许播放。
     *
     * @return 有效返回 {@code true}
     */
    public boolean canPlay() {
        return this == AUTHORIZED;
    }
}
