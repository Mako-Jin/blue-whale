package com.bluewhale.crm.service.domain.model;

/**
 * 满意度问卷状态。
 *
 * @author BlueWhale
 */
public enum SurveyStatus {

    /** 已发送，待答卷 */
    SENT,

    /** 已提交答卷 */
    USED,

    /** 已过期 */
    EXPIRED,

    /** 已作废 */
    REVOKED;

    /**
     * 解析状态，空值返回 null。
     *
     * @param value 状态字符串
     * @return 状态
     */
    public static SurveyStatus from(String value) {
        return value == null || value.isBlank() ? null : SurveyStatus.valueOf(value.trim());
    }
}
