package com.bluewhale.oa.approval.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审批结果同步命令（由 Workflow 终态对账或内网回调触发）。
 *
 * @author BlueWhale
 */
@Data
public class ApprovalResultCommand {

    /** 目标状态：APPROVED / REJECTED / CANCELLED / BROKEN */
    @NotBlank(message = "审批结果不能为空")
    @Pattern(regexp = "APPROVED|REJECTED|CANCELLED|BROKEN",
            message = "审批结果仅支持 APPROVED / REJECTED / CANCELLED / BROKEN")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
