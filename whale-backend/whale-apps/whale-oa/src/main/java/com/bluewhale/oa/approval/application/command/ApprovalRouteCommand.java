package com.bluewhale.oa.approval.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审批人快照命令（发起审批时由审批人解析结果生成）。
 *
 * @author BlueWhale
 */
@Data
public class ApprovalRouteCommand {

    /** Workflow 节点标识 */
    @NotBlank(message = "{validation.oa.approval-route.node-key-required}")
    @Size(max = 64, message = "{validation.oa.approval-route.node-key-size}")
    private String nodeKey;

    /** 节点名称 */
    @Size(max = 64, message = "{validation.oa.approval-route.node-name-size}")
    private String nodeName;

    /** 审批人用户ID */
    private Long assigneeId;

    /** 审批人账号 */
    @NotBlank(message = "{validation.oa.approval-route.assignee-account-required}")
    @Size(max = 64, message = "{validation.oa.approval-route.assignee-account-size}")
    private String assigneeAccount;

    /** 解析来源类型 */
    @Size(max = 32, message = "{validation.oa.approval-route.resolver-type-size}")
    private String resolverType;

    /** 排序号 */
    private Integer sortNo;
}
