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
    @NotNull(message = "{validation.mes.mes-maintenance-order.plan-id-required}")
    private Long planId;

    /** 应完成时间（空则按计划到期时间） */
    private LocalDateTime dueAt;

    /** 执行人 */
    @Size(max = 64, message = "{validation.mes.mes-maintenance-order.assignee-size}")
    private String assignee;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-maintenance-order.remark-size}")
    private String remark;
}
