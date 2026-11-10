package com.bluewhale.mes.equipment.dto;

import lombok.Data;

/**
 * 保养计划 / 工单分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesMaintenanceQueryRequest {

    /** 编号（模糊） */
    private String docNo;

    /** 设备 ID */
    private Long equipmentId;

    /** 设备编码（模糊） */
    private String equipmentCode;

    /** 状态 */
    private String status;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}
