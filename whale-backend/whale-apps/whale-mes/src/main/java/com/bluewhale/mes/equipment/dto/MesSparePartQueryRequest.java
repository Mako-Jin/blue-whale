package com.bluewhale.mes.equipment.dto;

import lombok.Data;

/**
 * 备件主档 / 库存 / 流水分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesSparePartQueryRequest {

    /** 备件编码（模糊） */
    private String partCode;

    /** 备件名称（模糊） */
    private String partName;

    /** 仓库编码 */
    private String warehouseCode;

    /** 状态 */
    private String status;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}
