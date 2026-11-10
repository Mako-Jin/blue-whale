package com.bluewhale.oa.approval.application.view;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批实例视图。
 *
 * @author BlueWhale
 */
@Data
public class ApprovalInstanceView implements Serializable {

    private static final long serialVersionUID = 1L;

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
    private String status;

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

    /** 审批人快照 */
    private List<ApprovalRouteView> routes;

    /** 业务副作用 */
    private List<ApprovalEffectView> effects;
}
