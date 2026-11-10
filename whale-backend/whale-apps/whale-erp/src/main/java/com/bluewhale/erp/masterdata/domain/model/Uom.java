package com.bluewhale.erp.masterdata.domain.model;

import lombok.Data;

/**
 * 计量单位（参考数据）。
 *
 * @author BlueWhale
 */
@Data
public class Uom {

    /** 主键 */
    private Long id;

    /** 单位编码 */
    private String code;

    /** 单位名称 */
    private String name;

    /** 数量小数位 */
    private Integer precisionNo;

    /** 状态 */
    private String status;
}
