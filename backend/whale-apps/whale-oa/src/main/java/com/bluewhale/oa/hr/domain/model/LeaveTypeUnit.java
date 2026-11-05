package com.bluewhale.oa.hr.domain.model;

import com.bluewhale.oa.common.OaErrorCode;

/**
 * 假期类型计量单位。
 *
 * @author BlueWhale
 */
public enum LeaveTypeUnit {

    /** 按天 */
    DAY,

    /** 按小时 */
    HOUR;

    /**
     * 解析计量单位文本。
     *
     * @param value 单位文本
     * @return 计量单位
     */
    public static LeaveTypeUnit of(String value) {
        if (value == null) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_TYPE_INVALID, "假期计量单位不能为空");
        }
        try {
            return LeaveTypeUnit.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_TYPE_INVALID,
                    "假期计量单位仅支持 DAY / HOUR");
        }
    }
}
