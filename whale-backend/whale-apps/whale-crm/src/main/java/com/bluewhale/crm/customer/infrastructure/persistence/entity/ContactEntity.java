package com.bluewhale.crm.customer.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 联系人实体，对应表 {@code crm_contact}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_contact")
public class ContactEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属客户 */
    private Long customerId;

    /** 姓名 */
    private String name;

    /** 电话 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 微信 */
    private String wechat;

    /** 职务 */
    private String title;

    /** 决策角色 */
    private String decisionRole;

    /** 是否关键决策人 */
    private Boolean isDecisionMaker;

    /** 是否主联系人 */
    private Boolean isPrimary;

    /** 状态 */
    private String status;

    /** 归一化电话 */
    private String normalizedPhone;

    /** 归一化邮箱 */
    private String normalizedEmail;

    /** 版本号 */
    private Integer version;
}
