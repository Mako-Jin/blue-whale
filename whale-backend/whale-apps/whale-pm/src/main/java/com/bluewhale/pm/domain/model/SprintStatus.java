package com.bluewhale.pm.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

import java.util.Locale;

/**
 * 迭代状态。
 *
 * @author BlueWhale
 */
public enum SprintStatus {

    /** 未启动 */
    FUTURE,

    /** 进行中 */
    ACTIVE,

    /** 已关闭 */
    CLOSED;

    /**
     * 解析迭代状态，非法值直接拒绝。
     *
     * @param code 状态编码（大小写不敏感）
     * @return 迭代状态
     */
    public static SprintStatus from(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "迭代状态不能为空");
        }
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "迭代状态仅支持 FUTURE / ACTIVE / CLOSED");
        }
    }
}
