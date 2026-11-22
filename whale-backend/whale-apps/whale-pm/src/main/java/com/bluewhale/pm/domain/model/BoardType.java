package com.bluewhale.pm.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

import java.util.Locale;

/**
 * 看板类型。
 *
 * @author BlueWhale
 */
public enum BoardType {

    /** 敏捷看板 */
    SCRUM,

    /** 看板方法 */
    KANBAN;

    /**
     * 解析看板类型，非法值直接拒绝。
     *
     * @param code 类型编码（大小写不敏感）
     * @return 看板类型
     */
    public static BoardType from(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pm.domain.kanban-type-empty", "看板类型不能为空");
        }
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pm.domain.kanban-type-supports-scrum-kanban", "看板类型仅支持 SCRUM / KANBAN");
        }
    }
}
