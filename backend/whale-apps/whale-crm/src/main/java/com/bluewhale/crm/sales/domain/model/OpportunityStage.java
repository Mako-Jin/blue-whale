package com.bluewhale.crm.sales.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

import java.math.BigDecimal;

/**
 * 商机阶段：接触、方案、谈判、赢单、输单。
 *
 * <p>状态机严格顺序推进：{@code CONTACT → PROPOSAL → NEGOTIATION → WON}；
 * 任一未关闭阶段可转 {@code LOST}，终态不可再流转。</p>
 *
 * @author BlueWhale
 */
public enum OpportunityStage {

    /** 接触 */
    CONTACT(new BigDecimal("10")),

    /** 方案 */
    PROPOSAL(new BigDecimal("30")),

    /** 谈判 */
    NEGOTIATION(new BigDecimal("60")),

    /** 赢单 */
    WON(new BigDecimal("100")),

    /** 输单 */
    LOST(BigDecimal.ZERO);

    /** 默认赢单概率 */
    private final BigDecimal defaultProbability;

    /**
     * 构造阶段并绑定默认赢单概率。
     *
     * @param defaultProbability 默认赢单概率
     */
    OpportunityStage(BigDecimal defaultProbability) {
        this.defaultProbability = defaultProbability;
    }

    /**
     * 解析阶段码（兼容存量 LEAD / QUALIFIED）。
     *
     * @param code 阶段码
     * @return 阶段枚举
     */
    public static OpportunityStage from(String code) {
        if (code == null || code.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.OPPORTUNITY_STAGE_CONFLICT);
        }
        String text = code.trim().toUpperCase();
        return switch (text) {
            case "CONTACT", "LEAD" -> CONTACT;
            case "PROPOSAL", "QUALIFIED" -> PROPOSAL;
            case "NEGOTIATION" -> NEGOTIATION;
            case "WON" -> WON;
            case "LOST" -> LOST;
            default -> throw CrmErrorCode.exception(CrmErrorCode.OPPORTUNITY_STAGE_CONFLICT);
        };
    }

    /**
     * 默认赢单概率。
     *
     * @return 概率（0–100）
     */
    public BigDecimal defaultProbability() {
        return defaultProbability;
    }

    /**
     * 是否为终态。
     *
     * @return 终态返回 true
     */
    public boolean terminal() {
        return this == WON || this == LOST;
    }

    /**
     * 判断是否允许流转到目标阶段。
     *
     * @param target 目标阶段
     * @return 允许返回 true
     */
    public boolean canMoveTo(OpportunityStage target) {
        if (target == null || terminal()) {
            return false;
        }
        if (target == LOST) {
            return true;
        }
        return switch (this) {
            case CONTACT -> target == PROPOSAL;
            case PROPOSAL -> target == NEGOTIATION;
            case NEGOTIATION -> target == WON;
            default -> false;
        };
    }
}
