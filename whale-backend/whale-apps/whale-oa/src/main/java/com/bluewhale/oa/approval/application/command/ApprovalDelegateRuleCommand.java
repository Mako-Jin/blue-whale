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
    @NotBlank(message = "流程编码不能为空")
    @Size(max = 64, message = "流程编码长度不能超过 64")
    private String definitionCode;

    /** 代理人账号 */
    @NotBlank(message = "代理人账号不能为空")
    @Size(max = 64, message = "代理人账号长度不能超过 64")
    private String toAccount;

    /** 生效开始时间 */
    @NotNull(message = "生效开始时间不能为空")
    private LocalDateTime startTime;

    /** 生效结束时间 */
    @NotNull(message = "生效结束时间不能为空")
    private LocalDateTime endTime;

    /** 是否启用 */
    private Boolean enabled;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过 200")
    private String remark;
}
