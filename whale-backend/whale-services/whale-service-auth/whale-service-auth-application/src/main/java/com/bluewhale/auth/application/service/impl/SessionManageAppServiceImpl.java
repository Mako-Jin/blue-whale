package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.model.query.LoginHistoryPageQuery;
import com.bluewhale.auth.application.model.query.SessionPageQuery;
import com.bluewhale.auth.application.model.vo.SessionVOs.DeviceVO;
import com.bluewhale.auth.application.model.vo.SessionVOs.LoginHistoryVO;
import com.bluewhale.auth.application.model.vo.SessionVOs.SessionVO;
import com.bluewhale.auth.application.port.CurrentUserProvider;
import com.bluewhale.auth.application.port.UserAccessRevocationPort;
import com.bluewhale.auth.application.service.SessionManageAppService;
import com.bluewhale.auth.domain.event.AuthDomainEvent;
import com.bluewhale.auth.domain.event.SessionDomainEvent;
import com.bluewhale.auth.domain.model.entity.DeviceRecord;
import com.bluewhale.auth.domain.model.entity.RefreshTokenRecord;
import com.bluewhale.auth.domain.repository.SessionLedgerRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 会话管理应用服务实现（权限中心扩展 M3）。
 *
 * <p>强制下线：账本记录标记 revoked（审计口径）+ {@link UserAccessRevocationPort#revoke}
 * 写入用户级撤销时刻（Redis 实时校验口径，即时生效）。租户维度过滤在本层校验，
 * 防止跨租户操作他人会话。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionManageAppServiceImpl implements SessionManageAppService {

    private final SessionLedgerRepository sessionLedgerRepository;
    private final UserAccessRevocationPort userAccessRevocationPort;
    private final CurrentUserProvider currentUserProvider;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 分页查询用户会话（refreshToken 记录账本）
     *
     * @param query 分页查询
     * @return 会话分页结果
     */
    @Override
    public PageResult<SessionVO> pageSessions(SessionPageQuery query) {
        long current = Math.max(query.current(), 1L);
        long size = Math.max(query.size(), 1L);
        Long tenantId = currentUserProvider.current().tokenTenantId();
        SessionLedgerRepository.RefreshTokenPage page =
                sessionLedgerRepository.pageRefreshTokens(tenantId, query.userId(), query.onlyActive(),
                        current, size);
        List<SessionVO> records = page.records().stream().map(SessionVO::from).toList();
        return PageResult.of(records, page.total(), current, size);
    }

    /**
     * 强制下线指定会话（按 jti）
     *
     * @param jti 会话令牌唯一标识
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeSession(String jti) {
        RefreshTokenRecord record = sessionLedgerRepository.findRefreshTokenByJti(jti)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "auth.session.not-found", "会话不存在或已结束"));
        ensureSameTenant(record.getTenantId());
        record.revoke(com.bluewhale.auth.domain.model.entity.RefreshTokenRecord.REASON_ADMIN_REVOKE);
        sessionLedgerRepository.saveRefreshToken(record);
        userAccessRevocationPort.revoke(record.getUserId());
        domainEventPublisher.publish(new SessionDomainEvent(record.getJti(), record.getDeviceId(),
                record.getTenantId(), record.getUserId(), AuthDomainEvent.ChangeType.SESSION_REVOKED,
                System.currentTimeMillis()));
        log.info("管理端强制下线会话: jti={}, userId={}, operator={}", jti, record.getUserId(),
                currentUserProvider.current().userId());
    }

    /**
     * 强制下线用户全部会话
     *
     * @param userId 用户 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeAllSessions(Long userId) {
        Long tenantId = currentUserProvider.current().tokenTenantId();
        boolean anyRevoked = false;
        long page = 1;
        while (true) {
            SessionLedgerRepository.RefreshTokenPage batch =
                    sessionLedgerRepository.pageRefreshTokens(tenantId, userId, true, page, 100);
            for (RefreshTokenRecord record : batch.records()) {
                record.revoke(com.bluewhale.auth.domain.model.entity.RefreshTokenRecord.REASON_ADMIN_REVOKE);
                sessionLedgerRepository.saveRefreshToken(record);
                anyRevoked = true;
            }
            if (batch.records().size() < 100 || page >= 1000) {
                break;
            }
            page++;
        }
        if (anyRevoked) {
            userAccessRevocationPort.revoke(userId);
            domainEventPublisher.publish(new SessionDomainEvent(null, null, tenantId, userId,
                    AuthDomainEvent.ChangeType.SESSION_REVOKED, System.currentTimeMillis()));
        }
        log.info("管理端强制下线用户全部会话: userId={}, operator={}", userId,
                currentUserProvider.current().userId());
    }

    /**
     * 查询用户设备列表（按最近使用时间降序）
     *
     * @param userId 用户 ID
     * @return 设备列表
     */
    @Override
    public List<DeviceVO> listDevices(Long userId) {
        Long tenantId = currentUserProvider.current().tokenTenantId();
        return sessionLedgerRepository.listDevicesByUser(tenantId, userId).stream()
                .map(DeviceVO::from)
                .toList();
    }

    /**
     * 标记 / 取消信任设备
     *
     * @param deviceId 设备主键
     * @param trusted 是否信任
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeDeviceTrust(Long deviceId, boolean trusted) {
        DeviceRecord device = sessionLedgerRepository.findDeviceById(deviceId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "auth.session.device-not-found", "设备不存在"));
        ensureSameTenant(device.getTenantId());
        if (trusted) {
            device.markTrusted();
        } else {
            device.untrust();
        }
        DeviceRecord saved = sessionLedgerRepository.saveDevice(device);
        domainEventPublisher.publish(new SessionDomainEvent(null, saved.getId(), saved.getTenantId(),
                saved.getUserId(), AuthDomainEvent.ChangeType.DEVICE_TRUSTED, System.currentTimeMillis()));
    }

    /**
     * 分页查询登录历史（按事件时间降序）
     *
     * @param query 分页查询
     * @return 登录历史分页结果
     */
    @Override
    public PageResult<LoginHistoryVO> pageLoginHistory(LoginHistoryPageQuery query) {
        long current = Math.max(query.current(), 1L);
        long size = Math.max(query.size(), 1L);
        Long tenantId = currentUserProvider.current().tokenTenantId();
        SessionLedgerRepository.LoginHistoryPage page =
                sessionLedgerRepository.pageLoginHistory(tenantId, query.userId(), query.result(),
                        query.startTime(), query.endTime(), current, size);
        List<LoginHistoryVO> records = page.records().stream().map(LoginHistoryVO::from).toList();
        return PageResult.of(records, page.total(), current, size);
    }

    /**
     * 校验目标记录属于当前登录租户，跨租户操作直接拒绝。
     *
     * @param recordTenantId 目标记录的租户主键
     */
    private void ensureSameTenant(Long recordTenantId) {
        Long currentTenantId = currentUserProvider.current().tokenTenantId();
        if (!currentTenantId.equals(recordTenantId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "auth.session.other-tenant-forbidden", "无权操作其他租户的会话");
        }
    }
}
