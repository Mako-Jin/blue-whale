package com.bluewhale.oa.approval.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审批转办命令。
 *
 * @author BlueWhale
 */
@Data
public class ApprovalTransferCommand {

    /** 目标账号 */
    @NotBlank(message = "转办目标账号不能为空")
    @Size(max = 64, message = "转办目标账号长度不能超过 64")
    private String targetUser;
}
