package com.bluewhale.crm.service.application.command;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 满意度问卷生成 / 重发命令。
 *
 * @author BlueWhale
 */
@Data
public class SurveySendCommand {

    /** 工单 ID */
    @NotNull
    private Long ticketId;
}
