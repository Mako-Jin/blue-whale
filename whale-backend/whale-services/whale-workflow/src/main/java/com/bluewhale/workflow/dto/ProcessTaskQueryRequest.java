package com.bluewhale.workflow.dto;

import com.bluewhale.common.core.query.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 待办任务分页查询条件。
 *
 * <p>{@code actorAccount}/{@code actorId} 用于「我审批的」视角；若同时传 {@code assignee}，
 * 两者按 AND 组合而非覆盖，调用方应避免混用。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProcessTaskQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 审批人账号（精确匹配，最长 64） */
    @Size(max = 64, message = "审批人账号长度不能超过 64")
    private String assignee;

    /** 状态：0 待办 / 1 已同意 / 2 已驳回 / 3 已取消（精确匹配） */
    private Integer status;

    /** 所属流程实例 ID（精确匹配） */
    private Long instanceId;

    /** 操作人用户ID（精确匹配；已审批任务） */
    private Long operatorId;

    /** 是否只看已处理：true 仅返回已同意/已驳回 */
    private Boolean handled;

    /** 「我审批的」视角账号（与 {@code actorId} 组合为 assignee OR operator_id；与 {@code assignee} 同时传时 AND 生效） */
    @Size(max = 64, message = "操作人账号长度不能超过 64")
    private String actorAccount;

    /** 「我审批的」视角用户ID（与 {@code actorAccount} 组合为 assignee OR operator_id） */
    private Long actorId;
}
