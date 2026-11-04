package com.bluewhale.erp.masterdata.application.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 客户视图。
 *
 * @author BlueWhale
 */
@Data
public class CustomerView {

    /** 主键 */
    private Long id;

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
