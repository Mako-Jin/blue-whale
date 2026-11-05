package com.bluewhale.pdm.collab.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目交付物分页查询条件
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PdmClbDeliverableQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** whale-pm 项目ID */
    private Long projectId;

    /** 对象类型：DOCUMENT/BOM/ROUTE */
    private String objectType;

    /** 交付物类型 */
    private String deliverableType;

    /** 关键字：对象编号/名称/项目名称 */
    private String keyword;
}
