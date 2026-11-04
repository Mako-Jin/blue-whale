package com.bluewhale.mes.equipment.dto;

import lombok.Data;

/**
 * OEE 快照分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesOeeQueryRequest {

    /** 设备 ID */
    private Long equipmentId;

    /** 设备编码（模糊） */
    private String equipmentCode;

    /** 班次日期（yyyy-MM-dd） */
    private String shiftDate;

    /** 班次编码 */
    private String shiftCode;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}
