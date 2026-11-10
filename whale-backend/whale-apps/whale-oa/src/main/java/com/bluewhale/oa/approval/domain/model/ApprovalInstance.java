package com.bluewhale.oa.approval.domain.model;

import com.bluewhale.oa.common.OaErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批实例聚合。
 *
 * <p>不变量：同一业务单据只允许一个审批实例；状态只能从 {@link ApprovalStatus#RUNNING}
 * 迁移到终态，重复同步同一终态返回 {@code false}（调用方据此避免重复执行副作用）。</p>
 *
 * @author BlueWhale
 */
@Data
public class ApprovalInstance {

    /** 主键 */
    private Long id;

    /** 业务类型 */
    private String businessType;

    /** 业务单据ID */
    private Long businessId;

    /** 业务单号 */
    private String businessCode;

    /** Workflow 流程定义编码 */
    private String definitionCode;

    /** Workflow 流程实例ID */
    private Long workflowInstanceId;

    /** 审批标题 */
    private String title;

    /** 发起人用户ID */
    private Long initiatorId;

    /** 发起人姓名快照 */
    private String initiatorName;

    /** 状态 */
    private ApprovalStatus status;

    /** 当前节点标识 */
    private String currentNodeKey;

    /** 当前节点名称 */
    private String currentNodeName;

    /** 当前审批人账号快照 */
    private String currentAssignees;

    /** 提交时间 */
    private LocalDateTime submittedAt;

    /** 完成时间 */
    private LocalDateTime finishedAt;

    /** 业务详情前端路由 */
    private String businessUrl;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 是否审批中。
     *
     * @return 审批中返回 {@code true}
     */
    public boolean running() {
        return status == ApprovalStatus.RUNNING;
    }

    /**
     * 绑定 Workflow 实例。
     *
     * @param workflowId Workflow 流程实例ID
     * @return 发生变更返回 {@code true}
     */
    public boolean attachWorkflow(Long workflowId) {
        if (workflowId == null) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_WORKFLOW_NOT_BOUND);
        }
        if (workflowInstanceId != null) {
            if (workflowInstanceId.equals(workflowId)) {
                return false;
            }
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_STATUS_CONFLICT, "审批实例已绑定其他流程实例");
        }
        workflowInstanceId = workflowId;
        return true;
    }

    /**
     * 更新当前节点与审批人快照（仅审批中允许）。
     *
     * @param nodeKey 节点标识
     * @param nodeName 节点名称
     * @param assignees 审批人账号快照
     */
    public void updateNode(String nodeKey, String nodeName, String assignees) {
        if (!running()) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_STATUS_CONFLICT);
        }
        currentNodeKey = nodeKey;
        currentNodeName = nodeName;
        currentAssignees = assignees;
    }

    /**
     * 迁移到终态。
     *
     * @param target 目标状态
     * @return 实际发生状态迁移返回 {@code true}；重复同步同一终态返回 {@code false}
     */
    public boolean markResult(ApprovalStatus target) {
        if (target == null || !target.terminal()) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_RESULT_INVALID);
        }
        if (status == target) {
            return false;
        }
        if (!running()) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_STATUS_CONFLICT,
                    "审批实例已结束（" + status + "），不能迁移到 " + target);
        }
        status = target;
        finishedAt = LocalDateTime.now();
        return true;
    }
}
