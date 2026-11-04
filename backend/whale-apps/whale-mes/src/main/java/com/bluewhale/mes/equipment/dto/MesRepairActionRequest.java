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
    @Size(max = 64, message = "维修人长度不能超过 64")
    private String assignee;

    /** 维修说明（验证） */
    @Size(max = 500, message = "维修说明长度不能超过 500")
    private String repairDesc;

    /** 验证人（验证） */
    @Size(max = 64, message = "验证人长度不能超过 64")
    private String verifiedBy;

    /** 验证结论（验证） */
    @Size(max = 255, message = "验证结论长度不能超过 255")
    private String verifyConclusion;

    /** 操作人（开始/关闭） */
    @Size(max = 64, message = "操作人长度不能超过 64")
    private String operator;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
