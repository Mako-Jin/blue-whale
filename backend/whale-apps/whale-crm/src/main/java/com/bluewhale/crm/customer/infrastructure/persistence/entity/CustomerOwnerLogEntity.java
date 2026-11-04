package com.bluewhale.crm.customer.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 负责人变更留痕实体，对应表 {@code crm_customer_owner_log}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_customer_owner_log")
public class CustomerOwnerLogEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 客户 ID */
    private Long customerId;

    /** 动作 */
    private String action;

    /** 原负责人 */
    private Long fromOwnerId;

    /** 新负责人 */
    private Long toOwnerId;

    /** 操作人 */
    private Long operatorId;

    /** 备注 */
    private String remark;
}
