package com.bluewhale.erp.masterdata.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 科目新增 / 编辑命令。
 *
 * @author BlueWhale
 */
@Data
public class AccountSaveCommand {

    /** 科目编码 */
    @NotBlank(message = "{validation.erp.account.code-required}")
    private String code;

    /** 科目名称 */
    @NotBlank(message = "{validation.erp.account.name-required}")
    private String name;

    /** 科目类型 */
    @NotBlank(message = "{validation.erp.account.account-type-required}")
    @Pattern(regexp = "ASSET|LIABILITY|EQUITY|REVENUE|EXPENSE", message = "{validation.erp.account.account-type-pattern}")
    private String accountType;

    /** 余额方向 */
    @NotBlank(message = "{validation.erp.account.direction-required}")
    @Pattern(regexp = "DEBIT|CREDIT", message = "{validation.erp.account.direction-pattern}")
    private String direction;

    /** 上级科目编码 */
    private String parentCode;

    /** 是否末级科目 */
    private Boolean leaf = Boolean.TRUE;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
