package com.bluewhale.erp.hr.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新建薪资批次命令。
 *
 * @author BlueWhale
 */
@Data
public class PayrollCreateCommand {

    /** 薪资期间（YYYY-MM，租户内唯一） */
    @NotBlank
    private String period;

    /** 备注 */
    private String remark;
}
