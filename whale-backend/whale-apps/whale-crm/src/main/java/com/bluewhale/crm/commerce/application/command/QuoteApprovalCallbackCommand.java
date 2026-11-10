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
    @NotNull(message = "流程实例不能为空")
    private Long instanceId;

    /** 工作流状态：1 通过 / 2 驳回 / 3 撤销 */
    @NotNull(message = "审批状态不能为空")
    private Integer status;

    /** 审批意见 */
    @Size(max = 500, message = "审批意见最多 500 个字符")
    private String comment;
}
