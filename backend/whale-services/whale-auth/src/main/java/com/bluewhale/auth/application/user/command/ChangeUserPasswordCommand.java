package com.bluewhale.auth.application.user.command;

/**
 * 重置用户口令命令：承载管理端「重置密码」用例的输入参数。
 *
 * @param rawPassword 新明文口令（落库前经 BCrypt 加密为密文）
 * @author BlueWhale
 */
public record ChangeUserPasswordCommand(String rawPassword) {
}
