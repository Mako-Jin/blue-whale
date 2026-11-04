package com.bluewhale.erp.masterdata.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 客户业务主数据实体，对应表 {@code erp_customer}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_customer")
public class CustomerEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 客户编码 */
    private String code;

    /** 客户名称 */
    private String name;

    /** 客户简称 */
    private String shortName;

    /** 纳税人识别号 */
    private String taxNo;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 信用额度 */
    private BigDecimal creditLimit;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
