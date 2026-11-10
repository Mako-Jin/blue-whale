package com.bluewhale.crm.sales.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 线索无效命令。
 *
 * @author BlueWhale
 */
@Data
public class LeadInvalidCommand {

    /** 无效原因 */
    @NotBlank(message = "无效原因不能为空")
    private String reason;
}
