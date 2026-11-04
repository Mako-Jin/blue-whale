package com.bluewhale.mes.quality.dto;

import lombok.Data;

/**
 * 处置分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesDispositionQueryRequest {

    /** 处置单号（模糊） */
    private String dispositionNo;

    /** NCR 编号 */
    private String ncrNo;

    /** 处置类型 */
    private String dispositionType;

    /** 状态 */
    private String status;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}
