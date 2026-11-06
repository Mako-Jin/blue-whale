package com.bluewhale.oa.hr.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

/**
 * 出差交通方式。
 *
 * @author BlueWhale
 */
public enum TripTransportType {

    /** 火车 */
    TRAIN,

    /** 飞机 */
    PLANE,

    /** 用车 */
    CAR,

    /** 其他 */
    OTHER;

    /**
     * 解析交通方式文本。
     *
     * @param value 交通方式文本
     * @return 交通方式
     */
    public static TripTransportType of(String value) {
        try {
            return TripTransportType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BizException(ErrorCode.PARAM_ERROR, "出差交通方式非法");
        }
    }
}
