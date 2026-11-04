package com.bluewhale.mes.trace.dto;

import lombok.Data;

/**
 * 补货请求分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesReplenishmentQueryRequest {

    /** 产线 ID */
    private Long lineId;

    /** 物料编码 */
    private String itemCode;

    /** 状态：REQUESTED / CONFIRMED / CANCELLED */
    private String status;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}
