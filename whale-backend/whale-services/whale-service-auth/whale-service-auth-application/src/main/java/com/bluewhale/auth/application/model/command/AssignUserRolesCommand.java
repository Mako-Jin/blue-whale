package com.bluewhale.auth.application.model.command;

import java.util.List;

/**
 * 分配用户角色命令：承载管理端「全量覆盖用户角色」用例的输入参数。
 *
 * @param roleIds 目标角色 ID 列表（全量覆盖；空列表表示清空用户角色）
 * @author BlueWhale
 */
public record AssignUserRolesCommand(List<Long> roleIds) {
}
