package com.bluewhale.mes.collect.dto;

import lombok.Data;

/**
 * 看板配置分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesCollectBoardQueryRequest {

    /** 看板编码（模糊） */
    private String boardCode;

    /** 看板名称（模糊） */
    private String boardName;

    /** 产线 ID */
    private Long lineId;

    /** 状态 */
    private String status;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}
