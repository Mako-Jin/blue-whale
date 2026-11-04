package com.bluewhale.crm.sales.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 线索状态推进命令（仅允许 CONTACTED / QUALIFIED）。
 *
 * @author BlueWhale
 */
@Data
public class LeadStatusCommand {

    /** 目标状态 */
    @NotBlank(message = "目标状态不能为空")
    private String status;
}
