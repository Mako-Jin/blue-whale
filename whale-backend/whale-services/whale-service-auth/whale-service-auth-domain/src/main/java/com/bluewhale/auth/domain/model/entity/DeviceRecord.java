package com.bluewhale.auth.domain.model.entity;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.LocalDateTime;

/**
 * 登录设备实体（对应 {@code sys_device}，权限中心扩展 M3）。
 *
 * <p>设备指纹 + 用户维度唯一（DB 唯一约束兜底）；信任标记与最近使用时间由本实体维护。</p>
 *
 * @author BlueWhale
 */
public class DeviceRecord implements Entity<Long> {

    private final Long id;
    private final Long tenantId;
    private final Long userId;
    private final String deviceFingerprint;
    private String deviceName;
    private String deviceType;
    private String os;
    private String browser;
    private boolean trusted;
    private LocalDateTime lastLoginTime;
    private final LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 私有构造器：集中校验实体不变量
     *
     * @param id 主键（新建时为 {@code null}）
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param deviceFingerprint 设备指纹
     * @param deviceName 设备名称（可空）
     * @param deviceType 设备类型（可空）
     * @param os 操作系统快照（可空）
     * @param browser 浏览器快照（可空）
     * @param trusted 是否信任设备
     * @param lastLoginTime 最近使用时间（可空）
     * @param createTime 创建时间
     * @param updateTime 更新时间
     */
    private DeviceRecord(Long id, Long tenantId, Long userId, String deviceFingerprint, String deviceName,
                         String deviceType, String os, String browser, boolean trusted,
                         LocalDateTime lastLoginTime, LocalDateTime createTime, LocalDateTime updateTime) {
        if (tenantId == null || userId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "设备必须归属租户与用户");
        }
        if (deviceFingerprint == null || deviceFingerprint.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "设备指纹不能为空");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.deviceFingerprint = deviceFingerprint.trim();
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.os = os;
        this.browser = browser;
        this.trusted = trusted;
        this.lastLoginTime = lastLoginTime;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 工厂方法：登记新设备
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param deviceFingerprint 设备指纹
     * @param deviceName 设备名称（可空）
     * @param deviceType 设备类型（可空）
     * @param os 操作系统快照（可空）
     * @param browser 浏览器快照（可空）
     * @return 设备实体
     */
    public static DeviceRecord create(Long tenantId, Long userId, String deviceFingerprint, String deviceName,
                                      String deviceType, String os, String browser) {
        LocalDateTime now = LocalDateTime.now();
        return new DeviceRecord(null, tenantId, userId, deviceFingerprint, deviceName, deviceType, os, browser,
                false, now, now, now);
    }

    /**
     * 由持久化数据重建实体
     *
     * @param id 主键
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param deviceFingerprint 设备指纹
     * @param deviceName 设备名称
     * @param deviceType 设备类型
     * @param os 操作系统快照
     * @param browser 浏览器快照
     * @param trusted 是否信任设备
     * @param lastLoginTime 最近使用时间
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 设备实体
     */
    public static DeviceRecord reconstitute(Long id, Long tenantId, Long userId, String deviceFingerprint,
                                            String deviceName, String deviceType, String os, String browser,
                                            Boolean trusted, LocalDateTime lastLoginTime,
                                            LocalDateTime createTime, LocalDateTime updateTime) {
        return new DeviceRecord(id, tenantId, userId, deviceFingerprint, deviceName, deviceType, os, browser,
                Boolean.TRUE.equals(trusted), lastLoginTime, createTime, updateTime);
    }

    /**
     * 行为：标记为信任设备
     */
    public void markTrusted() {
        this.trusted = true;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：取消信任
     */
    public void untrust() {
        this.trusted = false;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：刷新设备快照与最近使用时间
     *
     * @param newDeviceName 设备名称（可空，非空时覆盖）
     * @param newDeviceType 设备类型（可空，非空时覆盖）
     * @param newOs 操作系统快照（可空，非空时覆盖）
     * @param newBrowser 浏览器快照（可空，非空时覆盖）
     */
    public void recordUse(String newDeviceName, String newDeviceType, String newOs, String newBrowser) {
        if (newDeviceName != null && !newDeviceName.isBlank()) {
            this.deviceName = newDeviceName.trim();
        }
        if (newDeviceType != null && !newDeviceType.isBlank()) {
            this.deviceType = newDeviceType.trim();
        }
        if (newOs != null && !newOs.isBlank()) {
            this.os = newOs.trim();
        }
        if (newBrowser != null && !newBrowser.isBlank()) {
            this.browser = newBrowser.trim();
        }
        this.lastLoginTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 判断是否信任设备
     *
     * @return 信任返回 {@code true}
     */
    public boolean isTrusted() {
        return trusted;
    }

    /**
     * 获取主键
     *
     * @return 主键（新建时为 {@code null}）
     */
    @Override
    public Long getId() {
        return id;
    }

    /**
     * 获取租户 ID
     *
     * @return 租户 ID
     */
    public Long getTenantId() {
        return tenantId;
    }

    /**
     * 获取用户 ID
     *
     * @return 用户 ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 获取设备指纹
     *
     * @return 设备指纹
     */
    public String getDeviceFingerprint() {
        return deviceFingerprint;
    }

    /**
     * 获取设备名称
     *
     * @return 设备名称（可空）
     */
    public String getDeviceName() {
        return deviceName;
    }

    /**
     * 获取设备类型
     *
     * @return 设备类型（可空）
     */
    public String getDeviceType() {
        return deviceType;
    }

    /**
     * 获取操作系统快照
     *
     * @return 操作系统（可空）
     */
    public String getOs() {
        return os;
    }

    /**
     * 获取浏览器快照
     *
     * @return 浏览器（可空）
     */
    public String getBrowser() {
        return browser;
    }

    /**
     * 获取最近使用时间
     *
     * @return 最近使用时间（可空）
     */
    public LocalDateTime getLastLoginTime() {
        return lastLoginTime;
    }

    /**
     * 获取创建时间
     *
     * @return 创建时间
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 获取更新时间
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
}
