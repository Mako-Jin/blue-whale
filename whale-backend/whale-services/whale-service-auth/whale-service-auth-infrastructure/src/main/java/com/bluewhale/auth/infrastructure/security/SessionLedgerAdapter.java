package com.bluewhale.auth.infrastructure.security;

import com.bluewhale.auth.application.port.SessionLedgerPort;
import com.bluewhale.auth.domain.model.entity.DeviceRecord;
import com.bluewhale.auth.domain.model.entity.LoginHistoryRecord;
import com.bluewhale.auth.domain.model.entity.RefreshTokenRecord;
import com.bluewhale.auth.domain.repository.SessionLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 会话账本应用端口适配器：编排「设备登记 + 登录历史 + refreshToken 记录」写入
 * （权限中心扩展 M3，ADR #7）。
 *
 * <p>账本写入失败<b>不得阻断主流程</b>：所有编排步骤整体 try-catch，任一异常仅
 * {@code log.warn} 降级告警（安全告警场景 {@code auth:session:ledger}），绝不向上抛出；
 * Redis 仍是实时校验源，账本只提供会话可见性与管理端审计能力。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionLedgerAdapter implements SessionLedgerPort {

    /** 登录历史结果：成功 */
    private static final String RESULT_SUCCESS = "SUCCESS";
    /** 登录历史结果：失败 */
    private static final String RESULT_FAILURE = "FAILURE";
    /** 登录历史结果：登出 */
    private static final String RESULT_LOGOUT = "LOGOUT";
    /** 默认登出事件类型 */
    private static final String TYPE_LOGOUT = "LOGOUT";

    private final SessionLedgerRepository sessionLedgerRepository;

    /**
     * 记录一次登录 / 刷新 / 切换租户事件：指纹非空 upsert 设备 → 追加登录历史 →
     * jti 非空追加 refreshToken 会话记录；任一步失败仅告警不抛出
     *
     * @param entry 登录账本条目
     */
    @Override
    public void recordLogin(LoginLedgerEntry entry) {
        try {
            Long deviceId = resolveDeviceId(entry);
            sessionLedgerRepository.appendLoginHistory(buildLoginHistory(entry));
            appendRefreshTokenIfPresent(entry, deviceId);
        } catch (Exception ex) {
            log.warn("会话账本写入降级告警 scene=auth:session:ledger username={} jti={}",
                    entry.username(), entry.jti(), ex);
        }
    }

    /**
     * 记录一次会话结束：按 jti 撤销 refreshToken 记录并追加对应类型的登出历史；
     * jti 不在账本中时跳过撤销；任一步失败仅告警不抛出
     *
     * @param entry 会话结束条目
     */
    @Override
    public void recordSessionEnd(SessionEndEntry entry) {
        try {
            if (StringUtils.hasText(entry.jti())) {
                sessionLedgerRepository.findRefreshTokenByJti(entry.jti()).ifPresent(record -> {
                    record.revoke(entry.reason());
                    sessionLedgerRepository.saveRefreshToken(record);
                });
            }
            appendLogoutHistory(entry);
        } catch (Exception ex) {
            log.warn("会话账本写入降级告警 scene=auth:session:ledger username={} jti={}",
                    entry.username(), entry.jti(), ex);
        }
    }

    /**
     * 登记设备并返回设备 ID（指纹为空或租户/用户缺失时返回 {@code null}）
     *
     * @param entry 登录账本条目
     * @return 设备 ID；未登记设备返回 {@code null}
     */
    private Long resolveDeviceId(LoginLedgerEntry entry) {
        if (!StringUtils.hasText(entry.deviceFingerprint())
                || entry.tenantId() == null || entry.userId() == null) {
            return null;
        }
        DeviceRecord device = sessionLedgerRepository.upsertDevice(entry.tenantId(), entry.userId(),
                entry.deviceFingerprint(), entry.deviceName(), entry.deviceType(), entry.os(), entry.browser());
        return device.getId();
    }

    /**
     * 组装登录历史记录（成功或失败）
     *
     * @param entry 登录账本条目
     * @return 登录历史记录
     */
    private LoginHistoryRecord buildLoginHistory(LoginLedgerEntry entry) {
        if (entry.success()) {
            return LoginHistoryRecord.success(entry.tenantId(), entry.userId(), entry.username(),
                    entry.loginType(), entry.ip(), entry.userAgent(), entry.deviceFingerprint());
        }
        return LoginHistoryRecord.failure(entry.tenantId(), entry.userId(), entry.username(),
                entry.loginType(), entry.failureReason(), entry.ip(), entry.userAgent(),
                entry.deviceFingerprint());
    }

    /**
     * jti 非空时追加 refreshToken 会话记录（失败场景 jti 为空直接跳过）
     *
     * @param entry 登录账本条目
     * @param deviceId 关联设备 ID（可空）
     */
    private void appendRefreshTokenIfPresent(LoginLedgerEntry entry, Long deviceId) {
        if (!StringUtils.hasText(entry.jti())) {
            return;
        }
        sessionLedgerRepository.appendRefreshToken(RefreshTokenRecord.issue(entry.tenantId(), entry.userId(),
                entry.jti(), entry.familyId(), entry.issuedAt(), entry.expiresAt(), deviceId));
    }

    /**
     * 追加登出历史（username 为空时不写「用户名为空串」的历史，跳过该条）
     *
     * @param entry 会话结束条目
     */
    private void appendLogoutHistory(SessionEndEntry entry) {
        if (!StringUtils.hasText(entry.username()) || entry.tenantId() == null) {
            return;
        }
        String loginType = StringUtils.hasText(entry.loginType()) ? entry.loginType() : TYPE_LOGOUT;
        sessionLedgerRepository.appendLoginHistory(new LoginHistoryRecord(null, entry.tenantId(),
                entry.userId(), entry.username(), loginType, RESULT_LOGOUT, null,
                null, null, null, null));
    }
}
