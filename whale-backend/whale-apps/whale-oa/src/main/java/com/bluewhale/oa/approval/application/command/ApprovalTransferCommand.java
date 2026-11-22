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
    @NotBlank(message = "{validation.oa.approval-transfer.target-user-required}")
    @Size(max = 64, message = "{validation.oa.approval-transfer.target-user-size}")
    private String targetUser;
}
