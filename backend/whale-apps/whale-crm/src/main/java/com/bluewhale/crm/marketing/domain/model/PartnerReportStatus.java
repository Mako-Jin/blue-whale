package com.bluewhale.crm.marketing.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

import java.util.Locale;

/**
 * 渠道报单状态。
 *
 * <p>冲突判定：同一客户同一项目已有 {@code PENDING} / {@code APPROVED} 报单时，
 * 新报单进入 {@code CONFLICT}，仅 {@code CONFLICT} 允许人工裁决。</p>
 *
 * @author BlueWhale
 */
public enum PartnerReportStatus {

    /** 待审核 */
    PENDING,

    /** 已核准 */
    APPROVED,

    /** 已驳回 */
    REJECTED,

    /** 冲突待裁决 */
    CONFLICT;

    /**
     * 解析报单状态。
     *
     * @param value 文本
     * @return 报单状态
     */
    public static PartnerReportStatus from(String value) {
        if (value == null || value.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "渠道报单状态不能为空");
        }
        for (PartnerReportStatus status : values()) {
            if (status.name().equals(value.trim().toUpperCase(Locale.ROOT))) {
                return status;
            }
        }
        throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "渠道报单状态非法：" + value);
    }
}
