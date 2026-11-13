package com.bluewhale.auth.application.port;

import java.time.LocalDateTime;

/**
 * 会话账本端口（权限中心扩展 M3，ADR #7）。
 *
 * <p>登录 / 刷新 / 切换租户 / 登出时写入「登录历史 + 设备 + refreshToken 记录」账本；
 * <b>账本写入失败不得阻断主流程</b>——实现适配器必须整体 try-catch 并降级为
 * {@code log.warn} 告警（安全告警场景 {@code auth:session:ledger}）。Redis 仍是
 * 实时校验源，账本只提供会话可见性与管理端审计能力。</p>
 *
 * @author BlueWhale
 */
public interface SessionLedgerPort {

    /**
     * 记录一次登录 / 刷新 / 切换租户事件（成功或失败）
     *
     * <p>实现侧编排：指纹非空时 upsert 设备 → 追加登录历史 → jti 非空时追加
     * refreshToken 会话记录（关联设备 ID）。任一步失败仅告警不抛出。</p>
     *
     * @param entry 登录账本条目
     */
    void recordLogin(LoginLedgerEntry entry);

    /**
     * 记录一次会话结束（登出 / 管理端强制下线 / 轮换消费）
     *
     * <p>实现侧按 jti 标记 {@code sys_refresh_token.revoked}；jti 不在账本中时仅告警。</p>
     *
     * @param entry 会话结束条目
     */
    void recordSessionEnd(SessionEndEntry entry);

    /**
     * 登录账本条目：登录历史 + 设备快照 + 会话记录三合一入参
     *
     * @param tenantId 租户 ID（失败且租户未解析时可为 {@code null}）
     * @param userId 用户 ID（可空：用户不存在场景）
     * @param username 用户名
     * @param loginType 登录类型（PASSWORD / SOCIAL / OAUTH2 / SWITCH / REFRESH）
     * @param success 是否成功
     * @param failureReason 失败原因（成功时为 {@code null}）
     * @param ip 来源 IP（可空）
     * @param userAgent User-Agent（可空）
     * @param deviceFingerprint 设备指纹（可空：降级路径无指纹）
     * @param deviceName 设备名称（可空）
     * @param deviceType 设备类型（可空）
     * @param os 操作系统快照（可空）
     * @param browser 浏览器快照（可空）
     * @param jti 本次签发 refreshToken 的 jti（失败场景为 {@code null}）
     * @param familyId 令牌族 ID（失败场景为 {@code null}）
     * @param issuedAt refreshToken 签发时间（jti 为空时忽略）
     * @param expiresAt refreshToken 过期时间（jti 为空时忽略）
     * @author BlueWhale
     */
    record LoginLedgerEntry(Long tenantId, Long userId, String username, String loginType,
                            boolean success, String failureReason, String ip, String userAgent,
                            String deviceFingerprint, String deviceName, String deviceType,
                            String os, String browser,
                            String jti, String familyId, LocalDateTime issuedAt, LocalDateTime expiresAt) {
    }

    /**
     * 会话结束条目
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param username 用户名（可空）
     * @param jti 结束的 refreshToken jti（可空：仅记录登出历史）
     * @param reason 撤销原因（LOGOUT / ADMIN_REVOKE / FAMILY_REVOKED / ROTATED）
     * @param loginType 登录历史事件类型（LOGOUT / SWITCH / REFRESH）
     * @author BlueWhale
     */
    record SessionEndEntry(Long tenantId, Long userId, String username, String jti, String reason,
                           String loginType) {
    }
}
