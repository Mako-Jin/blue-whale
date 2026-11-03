package com.bluewhale.auth.application.user.password;

/**
 * 更新租户口令策略命令（REQ-SEC-023）。
 *
 * @param maxAgeDays 口令有效期（天）
 * @param warnDays 到期前提醒天数
 * @author BlueWhale
 */
public record UpdateTenantPasswordPolicyCommand(Integer maxAgeDays, Integer warnDays) {
}
