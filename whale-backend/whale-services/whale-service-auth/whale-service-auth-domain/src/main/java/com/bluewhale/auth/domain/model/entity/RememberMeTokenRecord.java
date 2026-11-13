package com.bluewhale.auth.domain.model.entity;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.LocalDateTime;

/**
 * 记住我令牌记录实体（对应 {@code sys_remember_me_token}，权限中心扩展 M3）。
 *
 * <p><b>偏差说明</b>：本里程碑仅建表落库（实体 / 仓储 / 迁移就绪），不接入 remember-me
 * 登录流程；为后续「记住我」的 series 轮换存储预留（series 同设备保持不变，验证后轮换
 * token，哈希落库禁止明文）。</p>
 *
 * @author BlueWhale
 */
public class RememberMeTokenRecord implements Entity<Long> {

    private final Long id;
    private final Long tenantId;
    private final Long userId;
    private final String series;
    private final String tokenHash;
    private final Long deviceId;
    private final LocalDateTime issuedAt;
    private LocalDateTime lastUsedAt;
    private final LocalDateTime expiresAt;
    private boolean revoked;
    private final LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 私有构造器：集中校验实体不变量
     *
     * @param id 主键（新建时为 {@code null}）
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param series 系列标识
     * @param tokenHash 令牌哈希（SHA-256）
     * @param deviceId 设备 ID（可空）
     * @param issuedAt 签发时间
     * @param lastUsedAt 最近使用时间（可空）
     * @param expiresAt 过期时间
     * @param revoked 是否已撤销
     * @param createTime 创建时间
     * @param updateTime 更新时间
     */
    private RememberMeTokenRecord(Long id, Long tenantId, Long userId, String series, String tokenHash,
                                  Long deviceId, LocalDateTime issuedAt, LocalDateTime lastUsedAt,
                                  LocalDateTime expiresAt, boolean revoked, LocalDateTime createTime,
                                  LocalDateTime updateTime) {
        if (tenantId == null || userId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "记住我令牌必须归属租户与用户");
        }
        if (series == null || series.isBlank() || tokenHash == null || tokenHash.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "记住我令牌 series 与哈希不能为空");
        }
        if (issuedAt == null || expiresAt == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "记住我令牌签发与过期时间不能为空");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.series = series;
        this.tokenHash = tokenHash;
        this.deviceId = deviceId;
        this.issuedAt = issuedAt;
        this.lastUsedAt = lastUsedAt;
        this.expiresAt = expiresAt;
        this.revoked = revoked;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 工厂方法：登记新记住我令牌
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param series 系列标识
     * @param tokenHash 令牌哈希
     * @param deviceId 设备 ID（可空）
     * @param issuedAt 签发时间
     * @param expiresAt 过期时间
     * @return 记住我令牌实体
     */
    public static RememberMeTokenRecord issue(Long tenantId, Long userId, String series, String tokenHash,
                                              Long deviceId, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        LocalDateTime now = LocalDateTime.now();
        return new RememberMeTokenRecord(null, tenantId, userId, series, tokenHash, deviceId,
                issuedAt, null, expiresAt, false, now, now);
    }

    /**
     * 由持久化数据重建实体
     *
     * @param id 主键
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param series 系列标识
     * @param tokenHash 令牌哈希
     * @param deviceId 设备 ID
     * @param issuedAt 签发时间
     * @param lastUsedAt 最近使用时间
     * @param expiresAt 过期时间
     * @param revoked 是否已撤销
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 记住我令牌实体
     */
    public static RememberMeTokenRecord reconstitute(Long id, Long tenantId, Long userId, String series,
                                                     String tokenHash, Long deviceId, LocalDateTime issuedAt,
                                                     LocalDateTime lastUsedAt, LocalDateTime expiresAt,
                                                     Boolean revoked, LocalDateTime createTime,
                                                     LocalDateTime updateTime) {
        return new RememberMeTokenRecord(id, tenantId, userId, series, tokenHash, deviceId, issuedAt,
                lastUsedAt, expiresAt, Boolean.TRUE.equals(revoked), createTime, updateTime);
    }

    /**
     * 行为：记录一次使用
     */
    public void touch() {
        this.lastUsedAt = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：撤销令牌（幂等）
     */
    public void revoke() {
        if (revoked) {
            return;
        }
        this.revoked = true;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 判断是否已撤销
     *
     * @return 已撤销返回 {@code true}
     */
    public boolean isRevoked() {
        return revoked;
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
     * 获取系列标识
     *
     * @return series
     */
    public String getSeries() {
        return series;
    }

    /**
     * 获取令牌哈希
     *
     * @return SHA-256 哈希
     */
    public String getTokenHash() {
        return tokenHash;
    }

    /**
     * 获取设备 ID
     *
     * @return 设备 ID（可空）
     */
    public Long getDeviceId() {
        return deviceId;
    }

    /**
     * 获取签发时间
     *
     * @return 签发时间
     */
    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    /**
     * 获取最近使用时间
     *
     * @return 最近使用时间（可空）
     */
    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    /**
     * 获取过期时间
     *
     * @return 过期时间
     */
    public LocalDateTime getExpiresAt() {
        return expiresAt;
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
