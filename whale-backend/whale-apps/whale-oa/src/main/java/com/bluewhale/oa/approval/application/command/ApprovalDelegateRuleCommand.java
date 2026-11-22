package com.bluewhale.oa.approval.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 代理规则保存命令。
 *
 * @author BlueWhale
 */
@Data
public class ApprovalDelegateRuleCommand {

    /** 流程定义编码 */
    @NotBlank(message = "{validation.oa.approval-delegate-rule.definition-code-required}")
    @Size(max = 64, message = "{validation.oa.approval-delegate-rule.definition-code-size}")
    private String definitionCode;

    /** 代理人账号 */
    @NotBlank(message = "{validation.oa.approval-delegate-rule.to-account-required}")
    @Size(max = 64, message = "{validation.oa.approval-delegate-rule.to-account-size}")
    private String toAccount;

    /** 生效开始时间 */
    @NotNull(message = "{validation.oa.approval-delegate-rule.start-time-required}")
    private LocalDateTime startTime;

    /** 生效结束时间 */
    @NotNull(message = "{validation.oa.approval-delegate-rule.end-time-required}")
    private LocalDateTime endTime;

    /** 是否启用 */
    private Boolean enabled;

    /** 备注 */
    @Size(max = 200, message = "{validation.oa.approval-delegate-rule.remark-size}")
    private String remark;
}
