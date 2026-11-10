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
    @NotBlank(message = "科目编码不能为空")
    private String code;

    /** 科目名称 */
    @NotBlank(message = "科目名称不能为空")
    private String name;

    /** 科目类型 */
    @NotBlank(message = "科目类型不能为空")
    @Pattern(regexp = "ASSET|LIABILITY|EQUITY|REVENUE|EXPENSE", message = "科目类型非法")
    private String accountType;

    /** 余额方向 */
    @NotBlank(message = "余额方向不能为空")
    @Pattern(regexp = "DEBIT|CREDIT", message = "余额方向非法")
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
