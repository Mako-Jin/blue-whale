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
    @NotBlank(message = "{validation.erp.erp-supplier.code-required}")
    @Size(max = 64, message = "{validation.erp.erp-supplier.code-size}")
    private String code;

    /** 供应商名称 */
    @NotBlank(message = "{validation.erp.erp-supplier.name-required}")
    @Size(max = 128, message = "{validation.erp.erp-supplier.name-size}")
    private String name;

    /** 供应商简称 */
    @Size(max = 64, message = "{validation.erp.erp-supplier.short-name-size}")
    private String shortName;

    /** 供应商分类 */
    @NotBlank(message = "{validation.erp.erp-supplier.category-required}")
    @Pattern(regexp = "MATERIAL|SERVICE|OUTSOURCE|LOGISTICS|OTHER",
            message = "{validation.erp.erp-supplier.category-pattern}")
    private String category;

    /** 联系人 */
    @Size(max = 64, message = "{validation.erp.erp-supplier.contact-name-size}")
    private String contactName;

    /** 联系电话 */
    @Size(max = 32, message = "{validation.erp.erp-supplier.contact-phone-size}")
    private String contactPhone;

    /** 联系邮箱 */
    @Email(message = "{validation.erp.erp-supplier.contact-email-email}")
    @Size(max = 128, message = "{validation.erp.erp-supplier.contact-email-size}")
    private String contactEmail;

    /** 纳税人识别号 */
    @Size(max = 64, message = "{validation.erp.erp-supplier.tax-no-size}")
    private String taxNo;

    /** 开户银行 */
    @Size(max = 128, message = "{validation.erp.erp-supplier.bank-name-size}")
    private String bankName;

    /** 银行账号 */
    @Size(max = 64, message = "{validation.erp.erp-supplier.bank-account-size}")
    private String bankAccount;

    /** 地址 */
    @Size(max = 255, message = "{validation.erp.erp-supplier.address-size}")
    private String address;

    /** 状态：ENABLED / DISABLED */
    @NotBlank(message = "{validation.erp.erp-supplier.status-required}")
    @Pattern(regexp = "ENABLED|DISABLED", message = "{validation.erp.erp-supplier.status-pattern}")
    private String status;

    /** 供应商评级：A / B / C / D */
    @Pattern(regexp = "A|B|C|D", message = "{validation.erp.erp-supplier.rating-pattern}")
    private String rating;

    /** 备注 */
    @Size(max = 255, message = "{validation.erp.erp-supplier.remark-size}")
    private String remark;
}
