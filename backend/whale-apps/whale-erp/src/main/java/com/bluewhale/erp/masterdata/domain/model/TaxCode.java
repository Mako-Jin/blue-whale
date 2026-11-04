package com.bluewhale.erp.masterdata.domain.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 税码（参考数据）。
 *
 * @author BlueWhale
 */
@Data
public class TaxCode {

    /** 主键 */
    private Long id;

    /** 税码 */
    private String code;

    /** 税码名称 */
    private String name;

    /** 税率（小数） */
    private BigDecimal taxRate;

    /** 类型：VAT_OUTPUT / VAT_INPUT / OTHER */
    private String taxType;

    /** 状态 */
    private String status;
}
