package com.bluewhale.auth.application.role.command;

/**
 * 更新角色命令：承载管理端「更新角色」用例的输入参数。
 *
 * <p>角色编码不可修改；{@code sortNo} 与 {@code status} 为空表示不变更对应字段。</p>
 *
 * @param roleName 角色名称（必填）
 * @param description 角色描述
 * @param sortNo 排序号，为空时按不变更处理
 * @param status 状态（1 启用 / 0 停用），为空时按不变更处理
 * @author BlueWhale
 */
public record UpdateRoleCommand(String roleName, String description, Integer sortNo, Integer status) {
}
