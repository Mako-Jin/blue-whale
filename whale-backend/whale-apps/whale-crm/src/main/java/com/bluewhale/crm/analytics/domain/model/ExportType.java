package com.bluewhale.crm.analytics.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

/**
 * 分析导出类型。
 *
 * @author BlueWhale
 */
public enum ExportType {

    /** 客户 */
    CUSTOMER("客户"),

    /** 商机 */
    OPPORTUNITY("商机"),

    /** 订单 */
    ORDER("订单"),

    /** 回款 */
    COLLECTION("回款"),

    /** 行动计划 */
    ACTION("行动计划");

    /** 中文名 */
    private final String label;

    /**
     * 构造导出类型。
     *
     * @param label 中文名
     */
    ExportType(String label) {
        this.label = label;
    }

    /**
     * 查询中文名。
     *
     * @return 中文名
     */
    public String getLabel() {
        return label;
    }

    /**
     * 解析导出类型。
     *
     * @param code 类型编码
     * @return 导出类型
     */
    public static ExportType from(String code) {
        if (code == null || code.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_EXPORT_INVALID, "导出类型不能为空");
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_EXPORT_INVALID, "导出类型非法：" + code);
        }
    }
}
