package com.bluewhale.oa.approval.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审批退回命令。
 *
 * @author BlueWhale
 */
@Data
public class ApprovalRollbackCommand {

    /** 目标节点键 */
    @NotBlank(message = "{validation.oa.approval-rollback.target-node-key-required}")
    @Size(max = 64, message = "{validation.oa.approval-rollback.target-node-key-size}")
    private String targetNodeKey;
}
