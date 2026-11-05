package com.bluewhale.pdm.collab.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 研发评审分页查询条件
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PdmClbReviewQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** whale-pm 项目ID */
    private Long projectId;

    /** 评审类型 */
    private String reviewType;

    /** 状态：DRAFT/CLOSED */
    private String status;

    /** 关键字：评审编号/主题/对象编号 */
    private String keyword;
}
