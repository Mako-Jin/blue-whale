package com.bluewhale.crm.analytics.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

/**
 * 分析指标编码。
 *
 * <p>指标口径（M6）：</p>
 * <ul>
 *   <li>{@code WON_AMOUNT}：商机 {@code stage=WON} 且阶段关闭时间落在统计区间的金额；</li>
 *   <li>{@code CONTRACT_AMOUNT}：合同台账金额；</li>
 *   <li>{@code COLLECTION_RECEIVED}：回款计划已到期金额（CRM 暂不存实收，先按到期计划额保守统计）；</li>
 *   <li>{@code AR_OVERDUE_AMOUNT}：应收快照逾期金额（1–30 + 31–60 + 61–90 + 90 天以上）；</li>
 *   <li>{@code CSAT_AVG}：满意度答卷平均分；</li>
 *   <li>{@code CUSTOMER_ACTIVE}：最近 30 天有跟进记录的客户数。</li>
 * </ul>
 *
 * @author BlueWhale
 */
public enum MetricCode {

    /** 线索新增 */
    LEAD_NEW("线索新增", "COUNT"),

    /** 线索转化 */
    LEAD_CONVERTED("线索转化", "COUNT"),

    /** 未关闭商机数量 */
    OPPORTUNITY_OPEN_COUNT("未关闭商机数", "COUNT"),

    /** 未关闭商机金额 */
    OPPORTUNITY_OPEN_AMOUNT("未关闭商机金额", "AMOUNT"),

    /** 赢单金额 */
    WON_AMOUNT("赢单金额", "AMOUNT"),

    /** 合同金额 */
    CONTRACT_AMOUNT("合同金额", "AMOUNT"),

    /** 开票金额 */
    INVOICE_AMOUNT("开票金额", "AMOUNT"),

    /** 回款实收 */
    COLLECTION_RECEIVED("回款实收", "AMOUNT"),

    /** 逾期应收金额 */
    AR_OVERDUE_AMOUNT("逾期应收金额", "AMOUNT"),

    /** 客户新增 */
    CUSTOMER_NEW("客户新增", "COUNT"),

    /** 活跃客户 */
    CUSTOMER_ACTIVE("活跃客户", "COUNT"),

    /** 满意度均值 */
    CSAT_AVG("满意度均值", "SCORE");

    /** 指标中文名 */
    private final String label;

    /** 指标单位 */
    private final String unit;

    /**
     * 构造指标编码。
     *
     * @param label 中文名
     * @param unit 单位
     */
    MetricCode(String label, String unit) {
        this.label = label;
        this.unit = unit;
    }

    /**
     * 查询指标中文名。
     *
     * @return 中文名
     */
    public String getLabel() {
        return label;
    }

    /**
     * 查询指标单位。
     *
     * @return 单位
     */
    public String getUnit() {
        return unit;
    }

    /**
     * 解析指标编码。
     *
     * @param code 指标编码
     * @return 指标枚举
     */
    public static MetricCode from(String code) {
        if (code == null || code.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "指标编码不能为空");
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "指标编码非法：" + code);
        }
    }
}
