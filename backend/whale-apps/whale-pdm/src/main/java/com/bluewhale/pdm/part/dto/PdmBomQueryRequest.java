package com.bluewhale.pdm.part.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * BOM 分页查询条件
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PdmBomQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** BOM 编号（模糊匹配） */
    private String bomNo;

    /** 产品编码（模糊匹配） */
    private String productCode;

    /** 视图：EBOM/PBOM/MBOM */
    private String view;

    /** 状态：DRAFT/RELEASED/OBSOLETE */
    private String status;
}
