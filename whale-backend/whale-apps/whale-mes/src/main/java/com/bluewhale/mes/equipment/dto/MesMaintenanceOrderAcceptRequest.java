package com.bluewhale.mes.equipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保养工单验收请求
 *
 * @author BlueWhale
 */
@Data
public class MesMaintenanceOrderAcceptRequest {

    /** 验收人 */
    @NotBlank(message = "{validation.mes.mes-maintenance-order-accept.accepted-by-required}")
    @Size(max = 64, message = "{validation.mes.mes-maintenance-order-accept.accepted-by-size}")
    private String acceptedBy;

    /** 验收结论 */
    @NotBlank(message = "{validation.mes.mes-maintenance-order-accept.conclusion-required}")
    @Size(max = 255, message = "{validation.mes.mes-maintenance-order-accept.conclusion-size}")
    private String conclusion;
}
