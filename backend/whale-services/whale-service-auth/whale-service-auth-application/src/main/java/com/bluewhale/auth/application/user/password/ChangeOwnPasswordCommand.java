package com.bluewhale.auth.application.user.password;

/**
 * 自助改密命令（REQ-SEC-023）：用户本人修改口令，需同时提供旧口令与新口令。
 *
 * @param oldPassword 旧明文口令（用于校验身份）
 * @param newPassword 新明文口令（落库前经策略校验与 BCrypt 加密）
 * @author BlueWhale
 */
public record ChangeOwnPasswordCommand(String oldPassword, String newPassword) {
}
