package com.bluewhale.mes.equipment.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 维修工单动作请求（派工 / 开始 / 验证 / 关闭按动作取用相应字段）
 *
 * @author BlueWhale
 */
@Data
public class MesRepairActionRequest {

    /** 维修人（派工） */
    @Size(max = 64, message = "{validation.mes.mes-repair-action.assignee-size}")
    private String assignee;

    /** 维修说明（验证） */
    @Size(max = 500, message = "{validation.mes.mes-repair-action.repair-desc-size}")
    private String repairDesc;

    /** 验证人（验证） */
    @Size(max = 64, message = "{validation.mes.mes-repair-action.verified-by-size}")
    private String verifiedBy;

    /** 验证结论（验证） */
    @Size(max = 255, message = "{validation.mes.mes-repair-action.verify-conclusion-size}")
    private String verifyConclusion;

    /** 操作人（开始/关闭） */
    @Size(max = 64, message = "{validation.mes.mes-repair-action.operator-size}")
    private String operator;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-repair-action.remark-size}")
    private String remark;
}
