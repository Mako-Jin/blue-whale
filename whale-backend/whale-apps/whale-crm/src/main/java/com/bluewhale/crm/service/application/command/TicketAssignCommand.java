package com.bluewhale.crm.service.application.command;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 工单分派命令。
 *
 * @author BlueWhale
 */
@Data
public class TicketAssignCommand {

    /** 处理人 ID */
    @NotNull
    private Long assigneeId;

    /** 处理人姓名 */
    private String assigneeName;
}
