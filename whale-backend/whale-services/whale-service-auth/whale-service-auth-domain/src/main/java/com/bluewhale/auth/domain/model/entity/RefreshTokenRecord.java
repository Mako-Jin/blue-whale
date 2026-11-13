package com.bluewhale.auth.domain.model.entity;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.LocalDateTime;

/**
 * refreshToken 会话记录实体（对应 {@code sys_refresh_token}，权限中心扩展 M3）。
 *
 * <p><b>口径</b>：Redis 仍是实时校验源；本实体只承载「会话可见性 + 管理端审计/吊销」。
 * 记录只能「新增 / 撤销」，不可修改历史字段（{@code jti / familyId / issuedAt} 为 final）。
 * 撤销语义与 {@code sys_refresh_token.revoked} 标记对齐；强制下线时管理端还需通过
 * 用户撤销通道写入吊销时刻以即时生效。</p>
 *
 * @author BlueWhale
 */
public class RefreshTokenRecord implements Entity<Long> {

    /** 撤销原因：登出 */
    public static final String REASON_LOGOUT = "LOGOUT";
    /** 撤销原因：管理端强制下线 */
    public static final String REASON_ADMIN_REVOKE = "ADMIN_REVOKE";
    /** 撤销原因：令牌族吊销（复用检测） */
    public static final String REASON_FAMILY_REVOKED = "FAMILY_REVOKED";
    /** 撤销原因：轮换消费 */
    public static final String REASON_ROTATED = "ROTATED";

    private final Long id;
    private final Long tenantId;
    private final Long userId;
    private final String jti;
    private final String familyId;
    private final LocalDateTime issuedAt;
    private final LocalDateTime expiresAt;
    private boolean revoked;
    private LocalDateTime revokedAt;
    private String revokedReason;
    private final Long deviceId;
    private final LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 私有构造器：集中校验实体不变量
     *
     * @param id 主键（新建时为 {@code null}）
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param jti 令牌唯一标识（JWT jti）
     * @param familyId 令牌族 ID
     * @param issuedAt 签发时间
     * @param expiresAt 过期时间
     * @param revoked 是否已撤销
     * @param revokedAt 撤销时间（可空）
     * @param revokedReason 撤销原因（可空）
     * @param deviceId 登录设备 ID（可空）
     * @param createTime 创建时间
     * @param updateTime 更新时间
     */
    private RefreshTokenRecord(Long id, Long tenantId, Long userId, String jti, String familyId,
                               LocalDateTime issuedAt, LocalDateTime expiresAt, boolean revoked,
                               LocalDateTime revokedAt, String revokedReason, Long deviceId,
                               LocalDateTime createTime, LocalDateTime updateTime) {
        if (tenantId == null || userId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "会话记录必须归属租户与用户");
        }
        if (jti == null || jti.isBlank() || familyId == null || familyId.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "会话记录 jti 与 familyId 不能为空");
        }
        if (issuedAt == null || expiresAt == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "会话记录签发与过期时间不能为空");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.jti = jti;
        this.familyId = familyId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.revoked = revoked;
        this.revokedAt = revokedAt;
        this.revokedReason = revokedReason;
        this.deviceId = deviceId;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 工厂方法：登记新会话记录（登录 / 刷新换发时写入）
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param jti 令牌唯一标识
     * @param familyId 令牌族 ID
     * @param issuedAt 签发时间
     * @param expiresAt 过期时间
     * @param deviceId 登录设备 ID（可空）
     * @return 会话记录实体
     */
    public static RefreshTokenRecord issue(Long tenantId, Long userId, String jti, String familyId,
                                           LocalDateTime issuedAt, LocalDateTime expiresAt, Long deviceId) {
        LocalDateTime now = LocalDateTime.now();
        return new RefreshTokenRecord(null, tenantId, userId, jti, familyId, issuedAt, expiresAt,
                false, null, null, deviceId, now, now);
    }

    /**
     * 由持久化数据重建实体
     *
     * @param id 主键
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param jti 令牌唯一标识
     * @param familyId 令牌族 ID
     * @param issuedAt 签发时间
     * @param expiresAt 过期时间
     * @param revoked 是否已撤销
     * @param revokedAt 撤销时间
     * @param revokedReason 撤销原因
     * @param deviceId 登录设备 ID
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 会话记录实体
     */
    public static RefreshTokenRecord reconstitute(Long id, Long tenantId, Long userId, String jti,
                                                  String familyId, LocalDateTime issuedAt,
                                                  LocalDateTime expiresAt, Boolean revoked,
                                                  LocalDateTime revokedAt, String revokedReason, Long deviceId,
                                                  LocalDateTime createTime, LocalDateTime updateTime) {
        return new RefreshTokenRecord(id, tenantId, userId, jti, familyId, issuedAt, expiresAt,
                Boolean.TRUE.equals(revoked), revokedAt, revokedReason, deviceId, createTime, updateTime);
    }

    /**
     * 行为：撤销会话（只允许撤销一次，重复撤销幂等跳过）
     *
     * @param reason 撤销原因
     */
    public void revoke(String reason) {
        if (revoked) {
            return;
        }
        this.revoked = true;
        this.revokedAt = LocalDateTime.now();
        this.revokedReason = reason == null || reason.isBlank() ? REASON_LOGOUT : reason;
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
     * 获取令牌唯一标识
     *
     * @return jti
     */
    public String getJti() {
        return jti;
    }

    /**
     * 获取令牌族 ID
     *
     * @return familyId
     */
    public String getFamilyId() {
        return familyId;
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
     * 获取过期时间
     *
     * @return 过期时间
     */
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    /**
     * 获取撤销时间
     *
     * @return 撤销时间（可空）
     */
    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    /**
     * 获取撤销原因
     *
     * @return 撤销原因（可空）
     */
    public String getRevokedReason() {
        return revokedReason;
    }

    /**
     * 获取登录设备 ID
     *
     * @return 设备 ID（可空）
     */
    public Long getDeviceId() {
        return deviceId;
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
