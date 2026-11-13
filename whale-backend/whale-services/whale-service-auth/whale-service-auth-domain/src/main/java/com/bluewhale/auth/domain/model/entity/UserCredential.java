package com.bluewhale.auth.domain.model.entity;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户证件实体（对应 {@code sys_user_credential}，权限中心扩展 M3）。
 *
 * <p>证件号以 AES-GCM 密文（Base64）形式在应用层加密后进入本实体，实体自身只保证
 * 「密文非空」；「同一证件类型下主证件唯一」由聚合根保证。只允许通过聚合根整体替换。</p>
 *
 * @author BlueWhale
 */
public class UserCredential implements Entity<Long> {

    private final Long id;
    private final String credentialType;
    private final String credentialNoEncrypted;
    private final String holderName;
    private final boolean primary;
    private final LocalDate issueDate;
    private final LocalDate expiryDate;
    private final LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 私有构造器：集中校验实体不变量
     *
     * @param id 主键（新建时为 {@code null}）
     * @param credentialType 证件类型（ID_CARD / PASSPORT / HK_MO_PASS / OTHER）
     * @param credentialNoEncrypted 证件号密文（Base64，禁止明文）
     * @param holderName 证件持有人姓名（可空）
     * @param primary 是否主证件
     * @param issueDate 签发日期（可空）
     * @param expiryDate 有效期至（可空）
     * @param createTime 创建时间
     * @param updateTime 更新时间
     */
    private UserCredential(Long id, String credentialType, String credentialNoEncrypted, String holderName,
                           boolean primary, LocalDate issueDate, LocalDate expiryDate,
                           LocalDateTime createTime, LocalDateTime updateTime) {
        if (credentialType == null || credentialType.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "证件类型不能为空");
        }
        if (credentialNoEncrypted == null || credentialNoEncrypted.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "证件号密文不能为空（必须加密后传入）");
        }
        this.id = id;
        this.credentialType = credentialType.trim();
        this.credentialNoEncrypted = credentialNoEncrypted;
        this.holderName = holderName;
        this.primary = primary;
        this.issueDate = issueDate;
        this.expiryDate = expiryDate;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 工厂方法：新建证件（密文由应用层 {@code SensitiveFieldCipher} 加密后传入）
     *
     * @param credentialType 证件类型
     * @param credentialNoEncrypted 证件号密文（Base64）
     * @param holderName 持有人姓名（可空）
     * @param primary 是否主证件
     * @param issueDate 签发日期（可空）
     * @param expiryDate 有效期至（可空）
     * @return 证件实体
     */
    public static UserCredential create(String credentialType, String credentialNoEncrypted, String holderName,
                                        boolean primary, LocalDate issueDate, LocalDate expiryDate) {
        LocalDateTime now = LocalDateTime.now();
        return new UserCredential(null, credentialType, credentialNoEncrypted, holderName, primary,
                issueDate, expiryDate, now, now);
    }

    /**
     * 由持久化数据重建实体
     *
     * @param id 主键
     * @param credentialType 证件类型
     * @param credentialNoEncrypted 证件号密文（Base64）
     * @param holderName 持有人姓名
     * @param primary 是否主证件
     * @param issueDate 签发日期
     * @param expiryDate 有效期至
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 证件实体
     */
    public static UserCredential reconstitute(Long id, String credentialType, String credentialNoEncrypted,
                                              String holderName, Boolean primary, LocalDate issueDate,
                                              LocalDate expiryDate, LocalDateTime createTime,
                                              LocalDateTime updateTime) {
        return new UserCredential(id, credentialType, credentialNoEncrypted, holderName,
                Boolean.TRUE.equals(primary), issueDate, expiryDate, createTime, updateTime);
    }

    /**
     * 判断是否主证件
     *
     * @return 主证件返回 {@code true}
     */
    public boolean isPrimary() {
        return primary;
    }

    /**
     * 获取证件类型
     *
     * @return 类型编码
     */
    public String getCredentialType() {
        return credentialType;
    }

    /**
     * 获取证件号密文
     *
     * @return AES-GCM 密文（Base64）
     */
    public String getCredentialNoEncrypted() {
        return credentialNoEncrypted;
    }

    /**
     * 获取持有人姓名
     *
     * @return 姓名（可空）
     */
    public String getHolderName() {
        return holderName;
    }

    /**
     * 获取签发日期
     *
     * @return 签发日期（可空）
     */
    public LocalDate getIssueDate() {
        return issueDate;
    }

    /**
     * 获取有效期至
     *
     * @return 有效期至（可空）
     */
    public LocalDate getExpiryDate() {
        return expiryDate;
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

    /**
     * 获取主键
     *
     * @return 主键（新建时为 {@code null}）
     */
    @Override
    public Long getId() {
        return id;
    }
}
