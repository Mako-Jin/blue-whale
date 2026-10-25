package com.bluewhale.pm.interfaces.dto;

import com.bluewhale.common.core.query.PageQuery;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作项分页查询条件
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkItemQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 项目ID */
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    /** 关键字（标题或工作项键模糊匹配） */
    private String keyword;

    /** 状态ID */
    private Long statusId;

    /** 负责人用户ID */
    private Long assigneeId;

    /** 类型：EPIC / STORY / TASK / BUG / SUBTASK */
    private String type;

    /** 迭代ID */
    private Long sprintId;

    /** 父工作项ID */
    private Long parentId;

    /** 优先级 */
    private String priority;
}
