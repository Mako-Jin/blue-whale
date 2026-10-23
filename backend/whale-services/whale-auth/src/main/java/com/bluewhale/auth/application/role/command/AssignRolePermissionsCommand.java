package com.bluewhale.auth.application.role.command;

import java.util.List;

/**
 * 分配角色权限命令：承载管理端「全量覆盖角色权限」用例的输入参数。
 *
 * @param permissionIds 目标权限点 ID 列表（全量覆盖；空列表表示清空角色权限）
 * @author BlueWhale
 */
public record AssignRolePermissionsCommand(List<Long> permissionIds) {
}
