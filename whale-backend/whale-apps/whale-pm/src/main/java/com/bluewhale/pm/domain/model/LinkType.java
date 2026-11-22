package com.bluewhale.pm.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

import java.util.Locale;

/**
 * 工作项关联类型。
 *
 * @author BlueWhale
 */
public enum LinkType {

    /** 阻塞 */
    BLOCKS,

    /** 被阻塞 */
    BLOCKED_BY,

    /** 相关 */
    RELATES,

    /** 重复 */
    DUPLICATES,

    /** 被重复 */
    DUPLICATED_BY;

    /**
     * 解析关联类型，非法值直接拒绝。
     *
     * @param code 关联类型编码（大小写不敏感）
     * @return 关联类型
     */
    public static LinkType from(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pm.domain.association-type-empty", "关联类型不能为空");
        }
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pm.domain.association-type-supports-blocks-blocked-relates",
                          "关联类型仅支持 BLOCKS / BLOCKED_BY / RELATES / DUPLICATES / DUPLICATED_BY");
        }
    }
}
