package com.bluewhale.erp.masterdata.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 客户业务主数据新增 / 编辑命令。
 *
 * @author BlueWhale
 */
@Data
public class CustomerSaveCommand {

    /** 客户编码 */
    @NotBlank(message = "客户编码不能为空")
    private String code;

    /** 客户名称 */
    @NotBlank(message = "客户名称不能为空")
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
