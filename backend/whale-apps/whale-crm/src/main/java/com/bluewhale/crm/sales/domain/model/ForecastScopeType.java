package com.bluewhale.crm.sales.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

import java.util.Locale;

/**
 * 预测 / 目标统计范围：个人或部门。
 *
 * @author BlueWhale
 */
public enum ForecastScopeType {

    /** 个人（负责人） */
    USER,

    /** 部门 */
    DEPT;

    /**
     * 解析范围类型，空白默认按个人。
     *
     * @param code 范围类型码
     * @return 范围类型
     */
    public static ForecastScopeType from(String code) {
        if (code == null || code.isBlank()) {
            return USER;
        }
        String text = code.trim().toUpperCase(Locale.ROOT);
        return switch (text) {
            case "USER" -> USER;
            case "DEPT" -> DEPT;
            default -> throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "统计范围只允许 USER / DEPT");
        };
    }
}
