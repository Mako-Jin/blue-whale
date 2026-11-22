package com.bluewhale.oa.approval.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 审批实例登记命令。
 *
 * <p>业务单据提交后由 OA 业务上下文调用：先落审批实例与审批人快照，
 * 再调用 Workflow 内网门面发起流程并回填 {@code workflowInstanceId}。</p>
 *
 * @author BlueWhale
 */
@Data
public class ApprovalInstanceRegisterCommand {

    /** 业务类型 */
    @NotBlank(message = "{validation.oa.approval-instance-register.business-type-required}")
    @Size(max = 64, message = "{validation.oa.approval-instance-register.business-type-size}")
    private String businessType;

    /** 业务单据ID */
    @NotNull(message = "{validation.oa.approval-instance-register.business-id-required}")
    private Long businessId;

    /** 业务单号 */
    @Size(max = 64, message = "{validation.oa.approval-instance-register.business-code-size}")
    private String businessCode;

    /** Workflow 流程定义编码；为空时按审批配置解析 */
    @Size(max = 64, message = "{validation.oa.approval-instance-register.definition-code-size}")
    private String definitionCode;

    /** 审批标题；为空时按标题模板与配置解析 */
    @Size(max = 255, message = "{validation.oa.approval-instance-register.title-size}")
    private String title;

    /** 发起人用户ID */
    private Long initiatorId;

    /** 发起人姓名快照 */
    @Size(max = 64, message = "{validation.oa.approval-instance-register.initiator-name-size}")
    private String initiatorName;

    /** 业务详情前端路由；为空时按审批配置解析 */
    @Size(max = 255, message = "{validation.oa.approval-instance-register.business-url-size}")
    private String businessUrl;

    /** 备注 */
    @Size(max = 255, message = "{validation.oa.approval-instance-register.remark-size}")
    private String remark;

    /** 审批人快照 */
    @Valid
    private List<ApprovalRouteCommand> routes;
}
