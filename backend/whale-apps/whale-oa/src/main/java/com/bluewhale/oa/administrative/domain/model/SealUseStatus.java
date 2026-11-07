package com.bluewhale.oa.administrative.domain.model;

import com.bluewhale.oa.common.OaErrorCode;

/**
 * 用章申请状态。
 *
 * @author BlueWhale
 */
public enum SealUseStatus {

    /** 待审批 */
    PENDING,

    /** 已通过 */
    APPROVED,

    /** 已驳回 */
    REJECTED,

    /** 已撤销 */
    CANCELLED;

    /**
     * 解析状态文本。
     *
     * @param value 状态文本
     * @return 状态枚举
     */
    public static SealUseStatus of(String value) {
        if (value == null || value.isBlank()) {
            throw OaErrorCode.exception(OaErrorCode.SEAL_STATUS_CONFLICT, "用章状态不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw OaErrorCode.exception(OaErrorCode.SEAL_STATUS_CONFLICT, "非法的用章状态：" + value);
        }
    }
}
