package com.bluewhale.mes.equipment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 保养工单创建请求（由计划生成，可指定应完成时间）
 *
 * @author BlueWhale
 */
@Data
public class MesMaintenanceOrderCreateRequest {

    /** 保养计划 ID */
    @NotNull(message = "保养计划不能为空")
    private Long planId;

    /** 应完成时间（空则按计划到期时间） */
    private LocalDateTime dueAt;

    /** 执行人 */
    @Size(max = 64, message = "执行人长度不能超过 64")
    private String assignee;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
