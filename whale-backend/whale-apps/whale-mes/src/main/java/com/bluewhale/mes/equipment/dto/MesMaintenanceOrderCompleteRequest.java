package com.bluewhale.mes.equipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 保养工单完工请求（保养项结论必须覆盖计划全部项）
 *
 * @author BlueWhale
 */
@Data
public class MesMaintenanceOrderCompleteRequest {

    /** 完工登记人 */
    @NotBlank(message = "{validation.mes.mes-maintenance-order-complete.completed-by-required}")
    @Size(max = 64, message = "{validation.mes.mes-maintenance-order-complete.completed-by-size}")
    private String completedBy;

    /** 保养项结论列表（项名=结论） */
    @NotEmpty(message = "{validation.mes.mes-maintenance-order-complete.checklist-results-required}")
    private List<String> checklistResults;

    /** 完工说明 */
    @Size(max = 255, message = "{validation.mes.mes-maintenance-order-complete.remark-size}")
    private String remark;
}
