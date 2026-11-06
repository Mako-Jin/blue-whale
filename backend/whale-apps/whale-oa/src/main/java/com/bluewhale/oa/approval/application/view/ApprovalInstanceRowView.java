package com.bluewhale.oa.approval.application.view;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审批中心流程实例行视图。
 *
 * @author BlueWhale
 */
@Data
public class ApprovalInstanceRowView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流程实例ID */
    private Long workflowInstanceId;

    /** 流程定义编码 */
    private String definitionCode;

    /** 实例标题 */
    private String title;

    /** 状态：0 运行中 / 1 已通过 / 2 已驳回 / 3 已撤销 */
    private Integer status;

    /** 状态文本 */
    private String statusName;

    /** 当前节点键 */
    private String currentNodeKey;

    /** 发起人账号 */
    private String initiator;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 业务类型 */
    private String businessType;

    /** 业务标题（本地审批实例标题） */
    private String businessTitle;

    /** 业务单号 */
    private String businessCode;

    /** 业务详情路由 */
    private String businessUrl;
}
