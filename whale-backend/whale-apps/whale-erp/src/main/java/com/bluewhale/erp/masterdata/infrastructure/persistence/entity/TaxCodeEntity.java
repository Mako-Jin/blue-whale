package com.bluewhale.erp.masterdata.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 税码实体，对应表 {@code erp_tax_code}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_tax_code")
public class TaxCodeEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 税码 */
    private String code;

    /** 税码名称 */
    private String name;

    /** 税率 */
    private BigDecimal taxRate;

    /** 类型 */
    private String taxType;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
