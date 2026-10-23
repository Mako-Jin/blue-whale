package com.bluewhale.auth.application.user.command;

/**
 * 变更用户状态命令：承载管理端「启停用户」用例的输入参数。
 *
 * @param status 目标状态（1 启用 / 0 停用），为空或非法值时由应用层抛出参数异常
 * @author BlueWhale
 */
public record ChangeUserStatusCommand(Integer status) {
}
