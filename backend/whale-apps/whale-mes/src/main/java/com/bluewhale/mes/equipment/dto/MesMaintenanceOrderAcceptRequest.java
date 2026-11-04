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
    @NotBlank(message = "验收人不能为空")
    @Size(max = 64, message = "验收人长度不能超过 64")
    private String acceptedBy;

    /** 验收结论 */
    @NotBlank(message = "验收结论不能为空")
    @Size(max = 255, message = "验收结论长度不能超过 255")
    private String conclusion;
}
