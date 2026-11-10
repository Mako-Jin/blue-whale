package com.bluewhale.oa.approval.application.command;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审批中心分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ApprovalCenterQuery extends PageQuery {

    /** 标题关键字 */
    private String keyword;

    /** 流程状态（Workflow 状态码） */
    private Integer status;

    /** 流程定义编码 */
    private String definitionCode;
}
