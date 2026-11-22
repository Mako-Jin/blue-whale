package com.bluewhale.crm.analytics.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 关闭行动项命令。
 *
 * @author BlueWhale
 */
@Data
public class ActionItemCloseCommand {

    /** 关闭原因（必填） */
    @NotBlank(message = "{validation.crm.action-item-close.reason-required}")
    @Size(max = 500, message = "{validation.crm.action-item-close.reason-size}")
    private String reason;
}
