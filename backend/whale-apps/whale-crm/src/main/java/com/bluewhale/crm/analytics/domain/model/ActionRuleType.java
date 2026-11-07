package com.bluewhale.crm.analytics.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

/**
 * 行动计划规则类型。
 *
 * @author BlueWhale
 */
public enum ActionRuleType {

    /** 客户长期未跟进 */
    CUSTOMER_IDLE("客户未跟进", 30),

    /** 商机预计关闭逾期 */
    OPPORTUNITY_OVERDUE("商机逾期", 0),

    /** 销售目标缺口 */
    TARGET_GAP("目标缺口", 0),

    /** 合同即将到期 */
    CONTRACT_EXPIRE("合同到期", 90),

    /** 应收逾期 */
    AR_OVERDUE("应收逾期", 0),

    /** 满意度低分 */
    CSAT_LOW("满意度低分", 0);

    /** 规则中文名 */
    private final String label;

    /** 默认阈值天数 */
    private final int defaultThresholdDays;

    /**
     * 构造规则类型。
     *
     * @param label 中文名
     * @param defaultThresholdDays 默认阈值天数
     */
    ActionRuleType(String label, int defaultThresholdDays) {
        this.label = label;
        this.defaultThresholdDays = defaultThresholdDays;
    }

    /**
     * 查询规则中文名。
     *
     * @return 中文名
     */
    public String getLabel() {
        return label;
    }

    /**
     * 查询默认阈值天数。
     *
     * @return 默认阈值天数
     */
    public int getDefaultThresholdDays() {
        return defaultThresholdDays;
    }

    /**
     * 解析规则类型。
     *
     * @param code 规则类型编码
     * @return 规则类型
     */
    public static ActionRuleType from(String code) {
        if (code == null || code.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "规则类型不能为空");
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "规则类型非法：" + code);
        }
    }
}
