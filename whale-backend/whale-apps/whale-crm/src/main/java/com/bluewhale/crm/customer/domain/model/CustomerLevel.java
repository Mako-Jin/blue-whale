package com.bluewhale.crm.customer.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

/**
 * 客户分级：A 重点、B 普通、C 潜力、D 观察。
 *
 * <p>兼容存量 {@code KEY / NORMAL / POTENTIAL}：KEY 映射 A，NORMAL 映射 C，
 * POTENTIAL 映射 D；后续只写 A/B/C/D。</p>
 *
 * @author BlueWhale
 */
public enum CustomerLevel {

    /** 重点客户 */
    A,

    /** 普通客户 */
    B,

    /** 潜力客户 */
    C,

    /** 观察客户 */
    D;

    /**
     * 解析等级码（含存量映射）。
     *
     * @param code 等级码
     * @return 等级枚举
     */
    public static CustomerLevel from(String code) {
        if (code == null || code.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_LEVEL_INVALID);
        }
        String text = code.trim().toUpperCase();
        return switch (text) {
            case "A", "KEY" -> A;
            case "B" -> B;
            case "C", "NORMAL" -> C;
            case "D", "POTENTIAL" -> D;
            default -> throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_LEVEL_INVALID, "客户等级非法：" + code);
        };
    }

    /**
     * 转为存量展示值。
     *
     * @param code 等级码
     * @return 规范等级码 A/B/C/D
     */
    public static String canonical(String code) {
        return from(code).name();
    }
}
