package com.bluewhale.oa.administrative.domain.model;

import com.bluewhale.oa.common.OaErrorCode;

/**
 * 用车预约状态。
 *
 * @author BlueWhale
 */
public enum BookingStatus {

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
    public static BookingStatus of(String value) {
        if (value == null || value.isBlank()) {
            throw OaErrorCode.exception(OaErrorCode.VEHICLE_BOOKING_STATUS_CONFLICT, "用车状态不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw OaErrorCode.exception(OaErrorCode.VEHICLE_BOOKING_STATUS_CONFLICT, "非法的用车状态：" + value);
        }
    }
}
