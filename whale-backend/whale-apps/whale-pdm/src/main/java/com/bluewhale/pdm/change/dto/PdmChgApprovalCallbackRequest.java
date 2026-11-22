package com.bluewhale.pdm.change.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 审批回调请求（Workflow → PDM 内网）
 *
 * @author BlueWhale
 */
@Data
public class PdmChgApprovalCallbackRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 审批实例ID */
    @NotNull(message = "{validation.pdm.pdm-chg-approval-callback.instance-id-required}")
    private Long instanceId;

    /** 工作流状态：0 运行中 / 1 通过 / 2 驳回 / 3 撤销 */
    @NotNull(message = "{validation.pdm.pdm-chg-approval-callback.status-required}")
    private Integer status;

    /** 审批意见 */
    private String comment;

    /** 操作人 */
    private Long operatorId;
}
