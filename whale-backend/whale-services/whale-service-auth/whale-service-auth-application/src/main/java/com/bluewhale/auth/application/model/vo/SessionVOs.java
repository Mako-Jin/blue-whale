package com.bluewhale.auth.application.model.vo;

import com.bluewhale.auth.domain.model.entity.DeviceRecord;
import com.bluewhale.auth.domain.model.entity.LoginHistoryRecord;
import com.bluewhale.auth.domain.model.entity.RefreshTokenRecord;

import java.time.LocalDateTime;

/**
 * 会话 / 设备 / 登录历史视图对象集合（权限中心扩展 M3）。
 *
 * @author BlueWhale
 */
public final class SessionVOs {

    /**
     * 会话视图对象
     *
     * @param jti 令牌唯一标识
     * @param familyId 令牌族 ID
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param issuedAt 签发时间
     * @param expiresAt 过期时间
     * @param revoked 是否已撤销
     * @param revokedAt 撤销时间
     * @param revokedReason 撤销原因
     * @param deviceId 设备 ID
     * @author BlueWhale
     */
    public record SessionVO(String jti, String familyId, Long tenantId, Long userId,
                            LocalDateTime issuedAt, LocalDateTime expiresAt, boolean revoked,
                            LocalDateTime revokedAt, String revokedReason, Long deviceId) {

        /**
         * 由会话记录装配
         *
         * @param record 会话记录实体
         * @return 视图对象
         */
        public static SessionVO from(RefreshTokenRecord record) {
            return new SessionVO(record.getJti(), record.getFamilyId(), record.getTenantId(),
                    record.getUserId(), record.getIssuedAt(), record.getExpiresAt(), record.isRevoked(),
                    record.getRevokedAt(), record.getRevokedReason(), record.getDeviceId());
        }
    }

    /**
     * 设备视图对象
     *
     * @param id 主键
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param deviceFingerprint 设备指纹
     * @param deviceName 设备名称
     * @param deviceType 设备类型
     * @param os 操作系统
     * @param browser 浏览器
     * @param trusted 是否信任设备
     * @param lastLoginTime 最近使用时间
     * @author BlueWhale
     */
    public record DeviceVO(Long id, Long tenantId, Long userId, String deviceFingerprint,
                           String deviceName, String deviceType, String os, String browser,
                           boolean trusted, LocalDateTime lastLoginTime) {

        /**
         * 由设备实体装配
         *
         * @param device 设备实体
         * @return 视图对象
         */
        public static DeviceVO from(DeviceRecord device) {
            return new DeviceVO(device.getId(), device.getTenantId(), device.getUserId(),
                    device.getDeviceFingerprint(), device.getDeviceName(), device.getDeviceType(),
                    device.getOs(), device.getBrowser(), device.isTrusted(), device.getLastLoginTime());
        }
    }

    /**
     * 登录历史视图对象
     *
     * @param id 主键
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param username 用户名
     * @param loginType 登录类型
     * @param loginResult 结果
     * @param failureReason 失败原因
     * @param ip 来源 IP
     * @param userAgent User-Agent
     * @param deviceFingerprint 设备指纹
     * @param loginTime 事件时间
     * @author BlueWhale
     */
    public record LoginHistoryVO(Long id, Long tenantId, Long userId, String username, String loginType,
                                 String loginResult, String failureReason, String ip, String userAgent,
                                 String deviceFingerprint, LocalDateTime loginTime) {

        /**
         * 由登录历史记录装配
         *
         * @param record 登录历史记录实体
         * @return 视图对象
         */
        public static LoginHistoryVO from(LoginHistoryRecord record) {
            return new LoginHistoryVO(record.getId(), record.tenantId(), record.userId(), record.username(),
                    record.loginType(), record.loginResult(), record.failureReason(), record.ip(),
                    record.userAgent(), record.deviceFingerprint(), record.loginTime());
        }
    }

    /**
     * 私有构造器：本类仅作为静态视图嵌套容器，不允许实例化。
     */
    private SessionVOs() {
    }
}
