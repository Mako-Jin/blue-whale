package com.bluewhale.crm.commerce.application.command;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商务订单审批回调命令（whale-workflow 回调）。
 *
 * @author BlueWhale
 */
@Data
public class SalesOrderApprovalCallbackCommand {

    /** 流程实例 ID */
    @NotNull
    private Long instanceId;

    /** 是否审批通过 */
    @NotNull
    private Boolean approved;

    /** 审批意见 */
    private String comment;
}
