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
    @NotBlank(message = "关闭行动项必须填写原因")
    @Size(max = 500, message = "关闭原因最多 500 字")
    private String reason;
}
