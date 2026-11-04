package com.bluewhale.oa.approval.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 审批实例实体，对应表 {@code oa_approval_instance}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_approval_instance")
public class ApprovalInstanceEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

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
}
