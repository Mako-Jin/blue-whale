package com.bluewhale.crm.customer.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 客户等级变更命令。
 *
 * @author BlueWhale
 */
@Data
public class CustomerLevelCommand {

    /** 等级 A/B/C/D */
    @NotBlank(message = "客户等级不能为空")
    private String level;
}
