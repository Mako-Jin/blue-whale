package com.bluewhale.oa.hr.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 加班申请状态变更命令（审批 / 撤销）。
 *
 * @author BlueWhale
 */
@Data
public class OvertimeStatusCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标状态 */
    @NotBlank(message = "目标状态不能为空")
    @Pattern(regexp = "APPROVED|REJECTED|CANCELLED",
            message = "目标状态仅支持 APPROVED / REJECTED / CANCELLED")
    private String status;
}
