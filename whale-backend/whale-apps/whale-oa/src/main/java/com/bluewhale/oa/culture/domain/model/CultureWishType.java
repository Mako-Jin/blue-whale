package com.bluewhale.oa.culture.domain.model;

import com.bluewhale.oa.common.OaErrorCode;

/**
 * 祝福类型。
 *
 * @author BlueWhale
 */
public enum CultureWishType {

    /** 生日 */
    BIRTHDAY,

    /** 入职周年 */
    ANNIVERSARY;

    /**
     * 解析祝福类型。
     *
     * @param value 类型字符串
     * @return 祝福类型
     */
    public static CultureWishType of(String value) {
        try {
            return CultureWishType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw OaErrorCode.exception(OaErrorCode.CULTURE_INVALID, "祝福类型非法：" + value);
        }
    }
}
