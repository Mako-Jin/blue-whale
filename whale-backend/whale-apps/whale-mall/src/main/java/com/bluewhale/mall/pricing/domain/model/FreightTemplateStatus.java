package com.bluewhale.mall.pricing.domain.model;

import java.util.Locale;

/**
 * 运费模板状态。
 *
 * @author BlueWhale
 */
public enum FreightTemplateStatus {

    /** 启用 */
    ACTIVE,

    /** 停用（已绑定该模板的渠道按「不收运费」处理，不会阻断下单） */
    DISABLED;

    /**
     * 解析状态文本。
     *
     * @param value 文本，可为 null
     * @return 状态；无法识别时回落到 {@link #ACTIVE}
     */
    public static FreightTemplateStatus parse(String value) {
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
