package com.bluewhale.oa.drive.domain.model;

import com.bluewhale.oa.common.OaErrorCode;

/**
 * 网盘文件夹范围。
 *
 * @author BlueWhale
 */
public enum DriveScope {

    /** 个人文件夹：仅所有者与显式授权对象可见 */
    PERSONAL,

    /** 部门文件夹：部门成员默认可见，所有者与授权对象可管理 */
    DEPARTMENT;

    /**
     * 解析范围，空值默认个人。
     *
     * @param value 范围字符串
     * @return 范围
     */
    public static DriveScope of(String value) {
        if (value == null || value.isBlank()) {
            return PERSONAL;
        }
        try {
            return DriveScope.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw OaErrorCode.exception(OaErrorCode.DRIVE_INVALID, "网盘文件夹范围非法：" + value);
        }
    }
}
