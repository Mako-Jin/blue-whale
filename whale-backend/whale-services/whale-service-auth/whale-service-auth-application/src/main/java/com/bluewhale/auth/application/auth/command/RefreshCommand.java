package com.bluewhale.auth.application.auth.command;

/**
 * 刷新令牌命令。
 *
 * @param refreshToken Cookie 中解析出的 refreshToken 原文
 * @author BlueWhale
 */
public record RefreshCommand(String refreshToken) {
}
