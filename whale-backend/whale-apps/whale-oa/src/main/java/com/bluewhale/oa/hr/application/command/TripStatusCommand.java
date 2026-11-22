package com.bluewhale.oa.hr.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 出差申请状态变更命令。
 *
 * @author BlueWhale
 */
@Data
public class TripStatusCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标状态：APPROVED / REJECTED / CANCELLED */
    @NotBlank(message = "{validation.oa.trip-status.status-required}")
    private String status;
}
