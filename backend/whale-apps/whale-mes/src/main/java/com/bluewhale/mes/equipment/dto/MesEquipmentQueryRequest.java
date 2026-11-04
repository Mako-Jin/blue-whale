package com.bluewhale.mes.equipment.dto;

import lombok.Data;

/**
 * 设备台账分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesEquipmentQueryRequest {

    /** 设备编码（模糊） */
    private String equipmentCode;

    /** 设备名称（模糊） */
    private String equipmentName;

    /** 设备类型 */
    private String equipmentType;

    /** 产线 ID */
    private Long lineId;

    /** 当前状态 */
    private String status;

    /** 是否启用：0 否 / 1 是 */
    private Integer enabled;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}
