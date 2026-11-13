package com.bluewhale.auth.infrastructure.persistence.assembler;

import com.bluewhale.auth.domain.model.entity.DeviceRecord;
import com.bluewhale.auth.domain.model.entity.LoginHistoryRecord;
import com.bluewhale.auth.domain.model.entity.RefreshTokenRecord;
import com.bluewhale.auth.domain.model.entity.RememberMeTokenRecord;
import com.bluewhale.auth.infrastructure.persistence.po.SysDevicePo;
import com.bluewhale.auth.infrastructure.persistence.po.SysLoginHistoryPo;
import com.bluewhale.auth.infrastructure.persistence.po.SysRefreshTokenPo;
import com.bluewhale.auth.infrastructure.persistence.po.SysRememberMeTokenPo;

/**
 * 会话账本实体与持久化对象装配器（权限中心扩展 M3）：
 * 覆盖设备、登录历史、refreshToken 记录、remember-me 记录四类转换。
 *
 * @author BlueWhale
 */
public final class SessionLedgerPersistenceAssembler {

    /**
     * 工具类禁止实例化
     */
    private SessionLedgerPersistenceAssembler() {
    }

    /**
     * 设备实体 → 持久化对象
     *
     * @param device 设备实体
     * @return 设备持久化对象
     */
    public static SysDevicePo toPo(DeviceRecord device) {
        SysDevicePo po = new SysDevicePo();
        po.setId(device.getId());
        po.setTenantId(device.getTenantId());
        po.setUserId(device.getUserId());
        po.setDeviceFingerprint(device.getDeviceFingerprint());
        po.setDeviceName(device.getDeviceName());
        po.setDeviceType(device.getDeviceType());
        po.setOs(device.getOs());
        po.setBrowser(device.getBrowser());
        po.setTrusted(device.isTrusted() ? 1 : 0);
        po.setLastLoginTime(device.getLastLoginTime());
        po.setCreateTime(device.getCreateTime());
        po.setUpdateTime(device.getUpdateTime());
        return po;
    }

    /**
     * 持久化对象 → 设备实体
     *
     * @param po 设备持久化对象
     * @return 设备实体
     */
    public static DeviceRecord toDevice(SysDevicePo po) {
        return DeviceRecord.reconstitute(po.getId(), po.getTenantId(), po.getUserId(),
                po.getDeviceFingerprint(), po.getDeviceName(), po.getDeviceType(), po.getOs(),
                po.getBrowser(), po.getTrusted() != null && po.getTrusted() == 1, po.getLastLoginTime(),
                po.getCreateTime(), po.getUpdateTime());
    }

    /**
     * 登录历史记录 → 持久化对象
     *
     * @param record 登录历史记录
     * @return 登录历史持久化对象
     */
    public static SysLoginHistoryPo toPo(LoginHistoryRecord record) {
        SysLoginHistoryPo po = new SysLoginHistoryPo();
        po.setId(record.getId());
        po.setTenantId(record.tenantId());
        po.setUserId(record.userId());
        po.setUsername(record.username());
        po.setLoginType(record.loginType());
        po.setLoginResult(record.loginResult());
        po.setFailureReason(record.failureReason());
        po.setIp(record.ip());
        po.setUserAgent(record.userAgent());
        po.setDeviceFingerprint(record.deviceFingerprint());
        po.setLoginTime(record.loginTime());
        return po;
    }

    /**
     * 持久化对象 → 登录历史记录
     *
     * @param po 登录历史持久化对象
     * @return 登录历史记录
     */
    public static LoginHistoryRecord toLoginHistory(SysLoginHistoryPo po) {
        return new LoginHistoryRecord(po.getId(), po.getTenantId(), po.getUserId(), po.getUsername(),
                po.getLoginType(), po.getLoginResult(), po.getFailureReason(), po.getIp(),
                po.getUserAgent(), po.getDeviceFingerprint(), po.getLoginTime());
    }

    /**
     * refreshToken 会话记录 → 持久化对象
     *
     * @param record 会话记录
     * @return refreshToken 持久化对象
     */
    public static SysRefreshTokenPo toPo(RefreshTokenRecord record) {
        SysRefreshTokenPo po = new SysRefreshTokenPo();
        po.setId(record.getId());
        po.setTenantId(record.getTenantId());
        po.setUserId(record.getUserId());
        po.setJti(record.getJti());
        po.setFamilyId(record.getFamilyId());
        po.setIssuedAt(record.getIssuedAt());
        po.setExpiresAt(record.getExpiresAt());
        po.setRevoked(record.isRevoked() ? 1 : 0);
        po.setRevokedAt(record.getRevokedAt());
        po.setRevokedReason(record.getRevokedReason());
        po.setDeviceId(record.getDeviceId());
        po.setCreateTime(record.getCreateTime());
        po.setUpdateTime(record.getUpdateTime());
        return po;
    }

    /**
     * 持久化对象 → refreshToken 会话记录
     *
     * @param po refreshToken 持久化对象
     * @return 会话记录
     */
    public static RefreshTokenRecord toRefreshToken(SysRefreshTokenPo po) {
        return RefreshTokenRecord.reconstitute(po.getId(), po.getTenantId(), po.getUserId(), po.getJti(),
                po.getFamilyId(), po.getIssuedAt(), po.getExpiresAt(), po.getRevoked() != null && po.getRevoked() == 1,
                po.getRevokedAt(), po.getRevokedReason(), po.getDeviceId(),
                po.getCreateTime(), po.getUpdateTime());
    }

    /**
     * remember-me 令牌记录 → 持久化对象
     *
     * @param record 记住我令牌记录
     * @return remember-me 持久化对象
     */
    public static SysRememberMeTokenPo toPo(RememberMeTokenRecord record) {
        SysRememberMeTokenPo po = new SysRememberMeTokenPo();
        po.setId(record.getId());
        po.setTenantId(record.getTenantId());
        po.setUserId(record.getUserId());
        po.setSeries(record.getSeries());
        po.setTokenHash(record.getTokenHash());
        po.setDeviceId(record.getDeviceId());
        po.setIssuedAt(record.getIssuedAt());
        po.setLastUsedAt(record.getLastUsedAt());
        po.setExpiresAt(record.getExpiresAt());
        po.setRevoked(record.isRevoked() ? 1 : 0);
        po.setCreateTime(record.getCreateTime());
        po.setUpdateTime(record.getUpdateTime());
        return po;
    }

    /**
     * 持久化对象 → remember-me 令牌记录
     *
     * @param po remember-me 持久化对象
     * @return 记住我令牌记录
     */
    public static RememberMeTokenRecord toRememberMeToken(SysRememberMeTokenPo po) {
        return RememberMeTokenRecord.reconstitute(po.getId(), po.getTenantId(), po.getUserId(), po.getSeries(),
                po.getTokenHash(), po.getDeviceId(), po.getIssuedAt(), po.getLastUsedAt(),
                po.getExpiresAt(), po.getRevoked() != null && po.getRevoked() == 1,
                po.getCreateTime(), po.getUpdateTime());
    }
}
