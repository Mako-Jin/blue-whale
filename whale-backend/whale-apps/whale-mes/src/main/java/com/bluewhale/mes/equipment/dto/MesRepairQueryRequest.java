package com.bluewhale.mes.equipment.dto;

import lombok.Data;

/**
 * 维修工单分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesRepairQueryRequest {

    /** 维修单号（模糊） */
    private String repairNo;

    /** 设备 ID */
    private Long equipmentId;

    /** 设备编码（模糊） */
    private String equipmentCode;

    /** 状态 */
    private String status;

    /** 严重度 */
    private String severity;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}
