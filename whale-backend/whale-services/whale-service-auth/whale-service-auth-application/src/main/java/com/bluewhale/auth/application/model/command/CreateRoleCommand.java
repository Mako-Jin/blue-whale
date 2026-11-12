package com.bluewhale.auth.application.model.command;

/**
 * 创建角色命令：承载管理端「创建角色」用例的输入参数。
 *
 * @param roleCode 角色编码（明文，经 {@code RoleCode} 值对象校验格式与全局唯一性）
 * @param roleName 角色名称
 * @param description 角色描述
 * @param sortNo 排序号，为空时按 0 处理
 * @param status 状态（1 启用 / 0 停用），为空时按启用处理
 * @author BlueWhale
 */
public record CreateRoleCommand(String roleCode, String roleName, String description,
                                Integer sortNo, Integer status) {
}
