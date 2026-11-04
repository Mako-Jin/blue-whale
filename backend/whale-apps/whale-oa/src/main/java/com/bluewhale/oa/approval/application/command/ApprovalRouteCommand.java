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
    @NotBlank(message = "节点标识不能为空")
    @Size(max = 64, message = "节点标识长度不能超过 64")
    private String nodeKey;

    /** 节点名称 */
    @Size(max = 64, message = "节点名称长度不能超过 64")
    private String nodeName;

    /** 审批人用户ID */
    private Long assigneeId;

    /** 审批人账号 */
    @NotBlank(message = "审批人账号不能为空")
    @Size(max = 64, message = "审批人账号长度不能超过 64")
    private String assigneeAccount;

    /** 解析来源类型 */
    @Size(max = 32, message = "解析来源类型长度不能超过 32")
    private String resolverType;

    /** 排序号 */
    private Integer sortNo;
}
