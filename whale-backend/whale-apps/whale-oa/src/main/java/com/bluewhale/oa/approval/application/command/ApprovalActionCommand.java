package com.bluewhale.oa.approval.application.command;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 审批同意/驳回命令。
 *
 * @author BlueWhale
 */
@Data
public class ApprovalActionCommand {

    /** 审批意见 */
    @Size(max = 500, message = "{validation.oa.approval-action.comment-size}")
    private String comment;

    /** 追加流程变量 */
    private Map<String, Object> variables;
}
