package com.bluewhale.crm.marketing.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

import java.util.Locale;

/**
 * 营销活动状态。
 *
 * <p>状态机 {@code DRAFT → RUNNING → ENDED}；{@code CANCELLED} 仅允许从
 * {@code DRAFT} 或 {@code RUNNING} 取消。</p>
 *
 * @author BlueWhale
 */
public enum CampaignStatus {

    /** 草稿 */
    DRAFT,

    /** 进行中 */
    RUNNING,

    /** 已结束 */
    ENDED,

    /** 已取消 */
    CANCELLED;

    /**
     * 解析活动状态。
     *
     * @param value 文本
     * @return 活动状态
     */
    public static CampaignStatus from(String value) {
        if (value == null || value.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "营销活动状态不能为空");
        }
        for (CampaignStatus status : values()) {
            if (status.name().equals(value.trim().toUpperCase(Locale.ROOT))) {
                return status;
            }
        }
        throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "营销活动状态非法：" + value);
    }
}
