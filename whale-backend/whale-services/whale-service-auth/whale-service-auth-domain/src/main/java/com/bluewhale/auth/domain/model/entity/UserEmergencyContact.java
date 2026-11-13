package com.bluewhale.auth.domain.model.entity;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.LocalDateTime;

/**
 * 用户紧急联系人实体（对应 {@code sys_user_emergency_contact}，权限中心扩展 M3）。
 *
 * <p>多条有序，只允许通过 {@code UserProfileAggregate} 整体替换。</p>
 *
 * @param id 主键（新建时为 {@code null}）
 * @param contactName 联系人姓名
 * @param relationship 与本人关系编码（可空）
 * @param phone 联系电话（可空）
 * @param sortNo 排序号
 * @param createTime 创建时间
 * @param updateTime 更新时间
 * @author BlueWhale
 */
public record UserEmergencyContact(Long id, String contactName, String relationship, String phone,
                                   Integer sortNo, LocalDateTime createTime, LocalDateTime updateTime)
        implements Entity<Long> {

    /**
     * 工厂方法：新建紧急联系人
     *
     * @param contactName 联系人姓名
     * @param relationship 与本人关系
     * @param phone 联系电话
     * @param sortNo 排序号
     * @return 紧急联系人实体
     */
    public static UserEmergencyContact create(String contactName, String relationship, String phone,
                                              Integer sortNo) {
        if (contactName == null || contactName.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "紧急联系人姓名不能为空");
        }
        return new UserEmergencyContact(null, contactName.trim(), relationship, phone,
                sortNo == null ? 0 : sortNo, LocalDateTime.now(), LocalDateTime.now());
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
