package com.bluewhale.oa.hr.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 请假申请状态变更命令（审批 / 撤销）。
 *
 * @author BlueWhale
 */
@Data
public class LeaveStatusCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标状态 */
    @NotBlank(message = "{validation.oa.leave-status.status-required}")
    @Pattern(regexp = "APPROVED|REJECTED|CANCELLED",
            message = "{validation.oa.leave-status.status-pattern}")
    private String status;
}
