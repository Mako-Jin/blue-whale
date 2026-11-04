package com.bluewhale.erp.masterdata.domain.model;

import lombok.Data;

/**
 * 币种（参考数据）。
 *
 * @author BlueWhale
 */
@Data
public class Currency {

    /** 主键 */
    private Long id;

    /** ISO 4217 币种代码 */
    private String code;

    /** 币种名称 */
    private String name;

    /** 币种符号 */
    private String symbol;

    /** 是否本位币 */
    private Boolean baseFlag;

    /** 状态 */
    private String status;
}
