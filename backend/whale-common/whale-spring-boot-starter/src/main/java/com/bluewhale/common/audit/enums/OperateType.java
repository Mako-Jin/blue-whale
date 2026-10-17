package com.bluewhale.common.audit.enums;

/**
 * 审计操作类型枚举，与 {@code audit_log.operate_type} 字段取值一一对应。
 *
 * @author BlueWhale
 */
public enum OperateType {

    /** 登录（含登录失败） */
    LOGIN,
    /** 登出 */
    LOGOUT,
    /** 新增 */
    CREATE,
    /** 修改 */
    UPDATE,
    /** 删除 */
    DELETE,
    /** 查询（默认不记录，仅审计查询类敏感场景显式标注） */
    QUERY,
    /** 授权变更：分配角色 / 分配权限 */
    GRANT,
    /** 导出 */
    EXPORT,
    /** 导入 */
    IMPORT,
    /** 其他敏感操作 */
    OTHER
}
