package com.bluewhale.oa.approval.application.command;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审批催办命令。
 *
 * @author BlueWhale
 */
@Data
public class ApprovalUrgeCommand {

    /** 被催办人账号；为空时催办当前全部待办人 */
    @Size(max = 64, message = "{validation.oa.approval-urge.target-account-size}")
    private String targetAccount;

    /** 催办说明 */
    @Size(max = 200, message = "{validation.oa.approval-urge.comment-size}")
    private String comment;
}
