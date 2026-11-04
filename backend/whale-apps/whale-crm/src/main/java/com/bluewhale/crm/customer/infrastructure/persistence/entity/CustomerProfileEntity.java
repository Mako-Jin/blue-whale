package com.bluewhale.crm.customer.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户画像实体，对应表 {@code crm_customer_profile}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_customer_profile")
public class CustomerProfileEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属客户 */
    private Long customerId;

    /** 偏好 */
    private String preference;

    /** 痛点 */
    private String painPoints;

    /** 决策链 JSON */
    private String decisionChain;

    /** 关键人 */
    private String keyPerson;

    /** 敏感备注 */
    private String sensitiveRemark;

    /** 版本号 */
    private Integer version;
}
