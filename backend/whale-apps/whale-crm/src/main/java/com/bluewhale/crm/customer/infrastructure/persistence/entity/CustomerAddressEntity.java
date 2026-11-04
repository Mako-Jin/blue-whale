package com.bluewhale.crm.customer.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户地址实体，对应表 {@code crm_customer_address}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_customer_address")
public class CustomerAddressEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属客户 */
    private Long customerId;

    /** 收件人 */
    private String contactName;

    /** 电话 */
    private String phone;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区县 */
    private String district;

    /** 详细地址 */
    private String detail;

    /** 是否默认收货 */
    private Boolean isDefaultShipping;

    /** 是否默认账单 */
    private Boolean isDefaultBilling;

    /** 状态 */
    private String status;
}
