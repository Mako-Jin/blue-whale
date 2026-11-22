package com.bluewhale.crm.commerce.application.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 报价审批回调命令。
 *
 * @author BlueWhale
 */
@Data
public class QuoteApprovalCallbackCommand {

    /** 流程实例 ID */
    @NotNull(message = "{validation.crm.quote-approval-callback.instance-id-required}")
    private Long instanceId;

    /** 工作流状态：1 通过 / 2 驳回 / 3 撤销 */
    @NotNull(message = "{validation.crm.quote-approval-callback.status-required}")
    private Integer status;

    /** 审批意见 */
    @Size(max = 500, message = "{validation.crm.quote-approval-callback.comment-size}")
    private String comment;
}
