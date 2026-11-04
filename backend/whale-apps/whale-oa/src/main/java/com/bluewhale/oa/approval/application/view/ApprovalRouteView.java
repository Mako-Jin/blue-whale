package com.bluewhale.oa.approval.application.view;

import lombok.Data;

import java.io.Serializable;

/**
 * 审批人快照视图。
 *
 * @author BlueWhale
 */
@Data
public class ApprovalRouteView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 节点标识 */
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
    private Integer sortNo;
}
