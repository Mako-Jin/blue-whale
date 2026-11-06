package com.bluewhale.oa.approval.application.view;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 抄送我的视图。
 *
 * @author BlueWhale
 */
@Data
public class ApprovalCcView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 抄送记录ID */
    private Long id;

    /** 流程实例ID */
    private Long instanceId;

    /** 抄送节点键 */
    private String nodeKey;

    /** 抄送节点名称 */
    private String nodeName;

    /** 已读标记 */
    private Integer readFlag;

    /** 已读时间 */
    private LocalDateTime readTime;

    /** 创建时间 */
    private LocalDateTime createTime;

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
}
