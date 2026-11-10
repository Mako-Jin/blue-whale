package com.bluewhale.oa.approval.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Workflow 抄送快照。
 *
 * @author BlueWhale
 */
@Data
public class WorkflowCcSnapshot {

    /** 抄送记录ID */
    private Long id;

    /** 流程实例ID */
    private Long instanceId;

    /** 抄送节点键 */
    private String nodeKey;

    /** 抄送节点名称 */
    private String nodeName;

    /** 收件人（账号或用户ID文本） */
    private String recipient;

    /** 已读标记：0 未读 / 1 已读 */
    private Integer readFlag;

    /** 已读时间 */
    private LocalDateTime readTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
