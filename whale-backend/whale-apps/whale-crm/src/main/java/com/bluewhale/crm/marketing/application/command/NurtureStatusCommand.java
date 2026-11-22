package com.bluewhale.crm.marketing.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 培育任务状态变更命令。
 *
 * @author BlueWhale
 */
@Data
public class NurtureStatusCommand {

    /** 目标状态：DONE / CANCELLED */
    @NotBlank(message = "{validation.crm.nurture-status.status-required}")
    private String status;
}
