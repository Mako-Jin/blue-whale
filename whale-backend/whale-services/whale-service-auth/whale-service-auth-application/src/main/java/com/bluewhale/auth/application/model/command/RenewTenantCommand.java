package com.bluewhale.auth.application.model.command;

import java.time.LocalDateTime;

/**
 * 租户续期命令。
 *
 * @param expireTime 新到期时间（必须晚于当前时间）
 * @author BlueWhale
 */
public record RenewTenantCommand(LocalDateTime expireTime) {
}
