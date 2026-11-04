package com.bluewhale.erp.masterdata.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 币种实体，对应表 {@code erp_currency}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_currency")
public class CurrencyEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 币种代码 */
    private String code;

    /** 币种名称 */
    private String name;

    /** 币种符号 */
    private String symbol;

    /** 是否本位币 */
    private Boolean baseFlag;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
