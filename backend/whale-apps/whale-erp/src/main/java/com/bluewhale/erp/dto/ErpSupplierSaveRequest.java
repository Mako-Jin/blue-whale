package com.bluewhale.erp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 供应商新增 / 更新请求。
 *
 * @author BlueWhale
 */
@Data
public class ErpSupplierSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 供应商编码（租户内唯一） */
    @NotBlank(message = "供应商编码不能为空")
    @Size(max = 64, message = "供应商编码长度不能超过 64")
    private String code;

    /** 供应商名称 */
    @NotBlank(message = "供应商名称不能为空")
    @Size(max = 128, message = "供应商名称长度不能超过 128")
    private String name;

    /** 供应商简称 */
    @Size(max = 64, message = "供应商简称长度不能超过 64")
    private String shortName;

    /** 供应商分类 */
    @NotBlank(message = "供应商分类不能为空")
    @Pattern(regexp = "MATERIAL|SERVICE|OUTSOURCE|LOGISTICS|OTHER",
            message = "供应商分类仅支持 MATERIAL / SERVICE / OUTSOURCE / LOGISTICS / OTHER")
    private String category;

    /** 联系人 */
    @Size(max = 64, message = "联系人长度不能超过 64")
    private String contactName;

    /** 联系电话 */
    @Size(max = 32, message = "联系电话长度不能超过 32")
    private String contactPhone;

    /** 联系邮箱 */
    @Email(message = "联系邮箱格式不正确")
    @Size(max = 128, message = "联系邮箱长度不能超过 128")
    private String contactEmail;

    /** 纳税人识别号 */
    @Size(max = 64, message = "纳税人识别号长度不能超过 64")
    private String taxNo;

    /** 开户银行 */
    @Size(max = 128, message = "开户银行长度不能超过 128")
    private String bankName;

    /** 银行账号 */
    @Size(max = 64, message = "银行账号长度不能超过 64")
    private String bankAccount;

    /** 地址 */
    @Size(max = 255, message = "地址长度不能超过 255")
    private String address;

    /** 状态：ENABLED / DISABLED */
    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "ENABLED|DISABLED", message = "状态仅支持 ENABLED / DISABLED")
    private String status;

    /** 供应商评级：A / B / C / D */
    @Pattern(regexp = "A|B|C|D", message = "供应商评级仅支持 A / B / C / D")
    private String rating;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
