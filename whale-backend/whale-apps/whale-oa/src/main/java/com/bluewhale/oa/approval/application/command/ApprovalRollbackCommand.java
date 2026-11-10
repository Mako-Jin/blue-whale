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
    @NotBlank(message = "退回节点不能为空")
    @Size(max = 64, message = "退回节点长度不能超过 64")
    private String targetNodeKey;
}
