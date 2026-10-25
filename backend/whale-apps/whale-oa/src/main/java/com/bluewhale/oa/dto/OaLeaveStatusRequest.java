package com.bluewhale.oa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 请假申请状态变更请求（审批 / 撤销）
 *
 * @author BlueWhale
 */
@Data
public class OaLeaveStatusRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标状态：APPROVED 通过 / REJECTED 驳回 / CANCELLED 撤销 */
    @NotBlank(message = "目标状态不能为空")
    @Pattern(regexp = "APPROVED|REJECTED|CANCELLED",
            message = "目标状态仅支持 APPROVED / REJECTED / CANCELLED")
    private String status;
}
