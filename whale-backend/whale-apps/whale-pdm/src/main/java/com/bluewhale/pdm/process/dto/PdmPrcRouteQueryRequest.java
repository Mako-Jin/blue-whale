package com.bluewhale.pdm.process.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 工艺路线分页查询条件。
 *
 * @author BlueWhale
 */
@Data
public class PdmPrcRouteQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页 */
    private Long current = 1L;

    /** 每页条数 */
    private Long size = 20L;

    /** 路线编号（模糊） */
    private String routeNo;

    /** 零件编码（模糊） */
    private String partCode;

    /** 状态：DRAFT/RELEASED/OBSOLETE */
    private String status;
}
