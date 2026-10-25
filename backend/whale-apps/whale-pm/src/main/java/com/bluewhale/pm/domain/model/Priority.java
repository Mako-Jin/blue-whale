package com.bluewhale.pm.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

import java.util.Locale;

/**
 * 工作项优先级：HIGHEST / HIGH / MEDIUM / LOW / LOWEST。
 *
 * @author BlueWhale
 */
public enum Priority {

    /** 最高 */
    HIGHEST,

    /** 高 */
    HIGH,

    /** 中 */
    MEDIUM,

    /** 低 */
    LOW,

    /** 最低 */
    LOWEST;

    /**
     * 解析优先级，非法值直接拒绝。
     *
     * @param code 优先级编码（大小写不敏感）
     * @return 优先级
     */
    public static Priority from(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "优先级不能为空");
        }
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "优先级仅支持 HIGHEST / HIGH / MEDIUM / LOW / LOWEST");
        }
    }
}
