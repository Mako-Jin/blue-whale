package com.bluewhale.oa.approval.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审批配置保存命令。
 *
 * @author BlueWhale
 */
@Data
public class ApprovalConfigSaveCommand {

    /** 业务类型 */
    @NotBlank(message = "{validation.oa.approval-config.business-type-required}")
    @Size(max = 64, message = "{validation.oa.approval-config.business-type-size}")
    private String businessType;

    /** 业务名称 */
    @NotBlank(message = "{validation.oa.approval-config.business-name-required}")
    @Size(max = 64, message = "{validation.oa.approval-config.business-name-size}")
    private String businessName;

    /** Workflow 流程定义编码 */
    @NotBlank(message = "{validation.oa.approval-config.definition-code-required}")
    @Size(max = 64, message = "{validation.oa.approval-config.definition-code-size}")
    private String definitionCode;

    /** 标题模板 */
    @Size(max = 255, message = "{validation.oa.approval-config.title-template-size}")
    private String titleTemplate;

    /** 业务详情前端路由 */
    @Size(max = 255, message = "{validation.oa.approval-config.business-url-size}")
    private String businessUrl;

    /** 默认抄送人ID，逗号分隔 */
    @Size(max = 512, message = "{validation.oa.approval-config.cc-user-ids-size}")
    private String ccUserIds;

    /** 备注 */
    @Size(max = 255, message = "{validation.oa.approval-config.remark-size}")
    private String remark;
}
