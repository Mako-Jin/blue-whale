package com.bluewhale.auth.application.auth.command;

/**
 * 登出命令。
 *
 * @param authorization 请求头原始值（Bearer 前缀，可空）
 * @param refreshToken Cookie 携带的 refreshToken（可空）
 * @author BlueWhale
 */
public record LogoutCommand(String authorization, String refreshToken) {
}
