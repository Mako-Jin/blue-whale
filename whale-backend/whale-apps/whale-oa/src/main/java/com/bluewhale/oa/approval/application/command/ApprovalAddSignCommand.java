package com.bluewhale.oa.approval.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 审批加签命令。
 *
 * @author BlueWhale
 */
@Data
public class ApprovalAddSignCommand {

    /** 加签账号列表 */
    @NotEmpty(message = "{validation.oa.approval-add-sign.assignees-required}")
    @Size(max = 10, message = "{validation.oa.approval-add-sign.assignees-size}")
    private List<@NotBlank(message = "{validation.oa.approval-add-sign.assignees-required}") @Size(max = 64, message = "{validation.oa.approval-approval-add-sign.assignees-size}") String> assignees;
}
