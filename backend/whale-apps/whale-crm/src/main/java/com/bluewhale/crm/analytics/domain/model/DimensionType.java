package com.bluewhale.crm.analytics.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

/**
 * 指标快照维度类型。
 *
 * <p>{@link #TOTAL} 表示租户全量口径，维度值固定为 {@code 0}；
 * 其余维度按负责人、部门、来源、行业拆分。</p>
 *
 * @author BlueWhale
 */
public enum DimensionType {

    /** 全量 */
    TOTAL,

    /** 按负责人 */
    OWNER,

    /** 按部门 */
    DEPT,

    /** 按来源 */
    SOURCE,

    /** 按行业 */
    INDUSTRY;

    /**
     * 解析维度类型。
     *
     * @param code 维度编码
     * @return 维度枚举
     */
    public static DimensionType from(String code) {
        if (code == null || code.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "维度类型不能为空");
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "维度类型非法：" + code);
        }
    }
}
