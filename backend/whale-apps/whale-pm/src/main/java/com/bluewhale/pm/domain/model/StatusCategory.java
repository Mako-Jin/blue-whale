package com.bluewhale.pm.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

import java.util.Locale;

/**
 * 工作项状态分类：TODO 未开始 / IN_PROGRESS 进行中 / DONE 已完成。
 *
 * @author BlueWhale
 */
public enum StatusCategory {

    /** 未开始 */
    TODO,

    /** 进行中 */
    IN_PROGRESS,

    /** 已完成 */
    DONE;

    /**
     * 解析状态分类编码，非法值直接拒绝。
     *
     * @param code 分类编码（大小写不敏感）
     * @return 状态分类
     */
    public static StatusCategory from(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "状态分类不能为空");
        }
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "状态分类仅支持 TODO / IN_PROGRESS / DONE");
        }
    }
}
