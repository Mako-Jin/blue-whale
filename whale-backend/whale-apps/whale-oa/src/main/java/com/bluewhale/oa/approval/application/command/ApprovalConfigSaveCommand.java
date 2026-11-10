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
    @NotBlank(message = "业务类型不能为空")
    @Size(max = 64, message = "业务类型长度不能超过 64")
    private String businessType;

    /** 业务名称 */
    @NotBlank(message = "业务名称不能为空")
    @Size(max = 64, message = "业务名称长度不能超过 64")
    private String businessName;

    /** Workflow 流程定义编码 */
    @NotBlank(message = "流程定义编码不能为空")
    @Size(max = 64, message = "流程定义编码长度不能超过 64")
    private String definitionCode;

    /** 标题模板 */
    @Size(max = 255, message = "标题模板长度不能超过 255")
    private String titleTemplate;

    /** 业务详情前端路由 */
    @Size(max = 255, message = "业务路由长度不能超过 255")
    private String businessUrl;

    /** 默认抄送人ID，逗号分隔 */
    @Size(max = 512, message = "抄送人长度不能超过 512")
    private String ccUserIds;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
