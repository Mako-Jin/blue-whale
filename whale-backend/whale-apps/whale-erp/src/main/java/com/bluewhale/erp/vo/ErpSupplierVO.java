package com.bluewhale.erp.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 供应商视图对象。
 *
 * @author BlueWhale
 */
@Data
public class ErpSupplierVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 供应商编码 */
    private String code;

    /** 供应商名称 */
    private String name;

    /** 供应商简称 */
    private String shortName;

    /** 供应商分类 */
    private String category;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 联系邮箱 */
    private String contactEmail;

    /** 纳税人识别号 */
    private String taxNo;

    /** 开户银行 */
    private String bankName;

    /** 银行账号 */
    private String bankAccount;

    /** 地址 */
    private String address;

    /** 状态 */
    private String status;

    /** 供应商评级 */
    private String rating;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
