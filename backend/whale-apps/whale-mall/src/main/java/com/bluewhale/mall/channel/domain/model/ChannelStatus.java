package com.bluewhale.mall.channel.domain.model;

import java.util.Locale;

/**
 * 渠道状态。
 *
 * @author BlueWhale
 */
public enum ChannelStatus {

    /** 营业中 */
    ACTIVE,

    /** 停业（不可下单，历史订单照旧可查） */
    DISABLED;

    /**
     * 解析状态文本。
     *
     * @param value 文本，可为 null
     * @return 状态；无法识别时回落到 {@link #ACTIVE}
     */
    public static ChannelStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ACTIVE;
        }
    }
}
