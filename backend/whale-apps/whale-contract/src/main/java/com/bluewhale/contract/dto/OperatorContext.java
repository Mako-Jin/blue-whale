package com.bluewhale.contract.dto;

/**
 * 当前操作人上下文（由网关可信头解析）。
 *
 * @param userId          用户ID文本（可为空）
 * @param username        用户账号
 * @param permissionCodes 权限码逗号串
 * @param roles           角色码逗号串（工作流内网调用需透传，用于 super_admin 等判定）
 * @author BlueWhale
 */
public record OperatorContext(String userId, String username, String permissionCodes, String roles) {
}
