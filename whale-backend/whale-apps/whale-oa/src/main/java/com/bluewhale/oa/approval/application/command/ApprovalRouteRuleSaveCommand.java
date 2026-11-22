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
    @NotBlank(message = "{validation.oa.approval-route-rule.business-type-required}")
    @Size(max = 64, message = "{validation.oa.approval-route-rule.business-type-size}")
    private String businessType;

    /** Workflow 节点标识 */
    @NotBlank(message = "{validation.oa.approval-route-rule.node-key-required}")
    @Size(max = 64, message = "{validation.oa.approval-route-rule.node-key-size}")
    private String nodeKey;

    /** 节点名称 */
    @Size(max = 64, message = "{validation.oa.approval-route-rule.node-name-size}")
    private String nodeName;

    /** 解析器类型 */
    @NotBlank(message = "{validation.oa.approval-route-rule.resolver-type-required}")
    @Pattern(regexp = "USER|ROLE|DEPT_LEADER|PARENT_DEPT_LEADER|INITIATOR|FIXED_ACCOUNT",
            message = "{validation.oa.approval-route-rule.resolver-type-pattern}")
    private String resolverType;

    /** 解析器参数 */
    @Size(max = 255, message = "{validation.oa.approval-route-rule.resolver-value-size}")
    private String resolverValue;

    /** 兜底解析器类型 */
    @Size(max = 32, message = "{validation.oa.approval-route-rule.fallback-resolver-type-size}")
    private String fallbackResolverType;

    /** 兜底解析器参数 */
    @Size(max = 255, message = "{validation.oa.approval-route-rule.fallback-resolver-value-size}")
    private String fallbackResolverValue;

    /** 排序号 */
    private Integer sortNo;

    /** 是否启用 */
    private Boolean enabled;

    /** 备注 */
    @Size(max = 255, message = "{validation.oa.approval-route-rule.remark-size}")
    private String remark;
}
