package com.bluewhale.oa.approval.application.view;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审批中心任务视图。
 *
 * @author BlueWhale
 */
@Data
public class ApprovalTaskView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private Long id;

    /** 流程实例ID */
    private Long instanceId;

    /** 节点键 */
    private String nodeKey;

    /** 节点名称 */
    private String nodeName;

    /** 审批人账号 */
    private String assignee;

    /** 任务归属人 */
    private String ownerId;

    /** 状态：0 待办 / 1 同意 / 2 驳回 / 3 取消 */
    private Integer status;

    /** 审批意见 */
    private String comment;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 业务类型 */
    private String businessType;

    /** 审批标题 */
    private String businessTitle;

    /** 业务单号 */
    private String businessCode;

    /** 业务详情路由 */
    private String businessUrl;

    /** 实例状态 */
    private String instanceStatus;

    /** 发起人姓名 */
    private String initiatorName;

    /** 当前用户是否可操作 */
    private Boolean canOperate;
}
