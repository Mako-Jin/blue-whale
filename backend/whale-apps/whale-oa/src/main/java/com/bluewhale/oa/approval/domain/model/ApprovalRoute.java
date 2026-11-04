package com.bluewhale.oa.approval.domain.model;

import lombok.Data;

/**
 * 审批人快照。
 *
 * @author BlueWhale
 */
@Data
public class ApprovalRoute {

    /** 主键 */
    private Long id;

    /** 审批实例ID */
    private Long approvalInstanceId;

    /** Workflow 节点标识 */
    private String nodeKey;

    /** 节点名称 */
    private String nodeName;

    /** 审批人用户ID */
    private Long assigneeId;

    /** 审批人账号 */
    private String assigneeAccount;

    /** 解析来源类型 */
    private String resolverType;

    /** 排序号 */
    private int sortNo;
}
