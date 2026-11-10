package com.bluewhale.oa.approval.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审批人解析规则保存命令。
 *
 * @author BlueWhale
 */
@Data
public class ApprovalRouteRuleSaveCommand {

    /** 业务类型 */
    @NotBlank(message = "业务类型不能为空")
    @Size(max = 64, message = "业务类型长度不能超过 64")
    private String businessType;

    /** Workflow 节点标识 */
    @NotBlank(message = "节点标识不能为空")
    @Size(max = 64, message = "节点标识长度不能超过 64")
    private String nodeKey;

    /** 节点名称 */
    @Size(max = 64, message = "节点名称长度不能超过 64")
    private String nodeName;

    /** 解析器类型 */
    @NotBlank(message = "解析器类型不能为空")
    @Pattern(regexp = "USER|ROLE|DEPT_LEADER|PARENT_DEPT_LEADER|INITIATOR|FIXED_ACCOUNT",
            message = "解析器类型仅支持 USER / ROLE / DEPT_LEADER / PARENT_DEPT_LEADER / INITIATOR / FIXED_ACCOUNT")
    private String resolverType;

    /** 解析器参数 */
    @Size(max = 255, message = "解析器参数长度不能超过 255")
    private String resolverValue;

    /** 兜底解析器类型 */
    @Size(max = 32, message = "兜底解析器类型长度不能超过 32")
    private String fallbackResolverType;

    /** 兜底解析器参数 */
    @Size(max = 255, message = "兜底解析器参数长度不能超过 255")
    private String fallbackResolverValue;

    /** 排序号 */
    private Integer sortNo;

    /** 是否启用 */
    private Boolean enabled;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
