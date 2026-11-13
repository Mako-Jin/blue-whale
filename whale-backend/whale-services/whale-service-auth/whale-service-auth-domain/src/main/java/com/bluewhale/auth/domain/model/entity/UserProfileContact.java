package com.bluewhale.auth.domain.model.entity;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.LocalDateTime;

/**
 * 用户联系方式实体（对应 {@code sys_user_contact}，权限中心扩展 M3）。
 *
 * <p>作为 {@code UserProfileAggregate} 的子实体存在，只允许通过聚合根整体替换；
 * 「同一联系方式类型下主联系方式唯一」由聚合根保证。</p>
 *
 * @author BlueWhale
 */
public class UserProfileContact implements Entity<Long> {

    private final Long id;
    private final String contactType;
    private final String contactValue;
    private final boolean primary;
    private boolean verified;
    private final int sortNo;
    private final LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 私有构造器：集中校验实体不变量
     *
     * @param id 主键（新建时为 {@code null}）
     * @param contactType 联系方式类型（MOBILE / EMAIL / WECHAT / DINGTALK / OTHER）
     * @param contactValue 联系方式值
     * @param primary 是否主联系方式
     * @param verified 是否已验证
     * @param sortNo 排序号
     * @param createTime 创建时间
     * @param updateTime 更新时间
     */
    private UserProfileContact(Long id, String contactType, String contactValue, boolean primary,
                               boolean verified, int sortNo, LocalDateTime createTime,
                               LocalDateTime updateTime) {
        if (contactType == null || contactType.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "联系方式类型不能为空");
        }
        if (contactValue == null || contactValue.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "联系方式值不能为空");
        }
        this.id = id;
        this.contactType = contactType.trim();
        this.contactValue = contactValue.trim();
        this.primary = primary;
        this.verified = verified;
        this.sortNo = sortNo;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 工厂方法：新建联系方式
     *
     * @param contactType 联系方式类型
     * @param contactValue 联系方式值
     * @param primary 是否主联系方式
     * @param verified 是否已验证
     * @param sortNo 排序号
     * @return 联系方式实体
     */
    public static UserProfileContact create(String contactType, String contactValue, boolean primary,
                                            boolean verified, int sortNo) {
        LocalDateTime now = LocalDateTime.now();
        return new UserProfileContact(null, contactType, contactValue, primary, verified, sortNo, now, now);
    }

    /**
     * 由持久化数据重建实体
     *
     * @param id 主键
     * @param contactType 联系方式类型
     * @param contactValue 联系方式值
     * @param primary 是否主联系方式
     * @param verified 是否已验证
     * @param sortNo 排序号
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 联系方式实体
     */
    public static UserProfileContact reconstitute(Long id, String contactType, String contactValue,
                                                  Boolean primary, Boolean verified, Integer sortNo,
                                                  LocalDateTime createTime, LocalDateTime updateTime) {
        return new UserProfileContact(id, contactType, contactValue,
                Boolean.TRUE.equals(primary), Boolean.TRUE.equals(verified),
                sortNo == null ? 0 : sortNo, createTime, updateTime);
    }

    /**
     * 行为：标记为已验证
     */
    public void markVerified() {
        this.verified = true;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 判断是否主联系方式
     *
     * @return 主联系方式返回 {@code true}
     */
    public boolean isPrimary() {
        return primary;
    }

    /**
     * 获取联系方式类型
     *
     * @return 类型编码
     */
    public String getContactType() {
        return contactType;
    }

    /**
     * 获取联系方式值
     *
     * @return 联系方式值
     */
    public String getContactValue() {
        return contactValue;
    }

    /**
     * 判断是否已验证
     *
     * @return 已验证返回 {@code true}
     */
    public boolean isVerified() {
        return verified;
    }

    /**
     * 获取排序号
     *
     * @return 排序号
     */
    public int getSortNo() {
        return sortNo;
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
