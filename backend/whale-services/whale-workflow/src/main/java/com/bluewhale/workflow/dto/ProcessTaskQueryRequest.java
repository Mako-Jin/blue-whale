package com.bluewhale.workflow.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 待办任务分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProcessTaskQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 审批人账号（精确匹配） */
    private String assignee;

    /** 状态：0 待办 / 1 已同意 / 2 已驳回 / 3 已取消（精确匹配） */
    private Integer status;

    /** 所属流程实例 ID（精确匹配） */
    private Long instanceId;
}
