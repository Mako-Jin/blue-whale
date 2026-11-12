package com.bluewhale.forum.common.enums;

/**
 * 版块可见性（对应 {@code fm_board.visibility}）。
 *
 * <p>三档全部用<b>数据范围</b>表达，不新增「部门管理员」之类角色：
 * 平台 RBAC 只有角色 + 数据范围，凭空造角色会让授权 JOIN 为空、权限判定静默失效。</p>
 *
 * @author BlueWhale
 */
public enum BoardVisibility {

    /** 全员可见：登录即可浏览 */
    ALL,

    /** 角色可见：命中 visible_roles 白名单才可见 */
    ROLE,

    /** 部门可见：命中 visible_depts 或当前用户所属部门才可见 */
    DEPT
}
