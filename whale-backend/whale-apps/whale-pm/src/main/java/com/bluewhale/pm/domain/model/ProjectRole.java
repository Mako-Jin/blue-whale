package com.bluewhale.pm.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

import java.util.Locale;

/**
 * 项目角色：ADMIN 管理员 / MEMBER 成员 / VIEWER 只读。
 *
 * @author BlueWhale
 */
public enum ProjectRole {

    /** 项目管理员：可维护成员、状态与流转配置 */
    ADMIN,

    /** 项目成员：可读写工作项 */
    MEMBER,

    /** 只读成员 */
    VIEWER;

    /**
     * 解析项目角色编码，非法值直接拒绝。
     *
     * @param code 角色编码（大小写不敏感）
     * @return 项目角色
     */
    public static ProjectRole from(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pm.domain.project-role-empty", "项目角色不能为空");
        }
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pm.domain.project-role-supports-admin-member-viewer",
                          "项目角色仅支持 ADMIN / MEMBER / VIEWER");
        }
    }
}
