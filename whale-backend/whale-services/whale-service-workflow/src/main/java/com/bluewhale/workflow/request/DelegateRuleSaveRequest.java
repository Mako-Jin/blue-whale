package com.bluewhale.workflow.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 代理规则保存请求。
 *
 * @author BlueWhale
 */
@Data
public class DelegateRuleSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流程编码（必填） */
    @NotBlank(message = "{validation.workflow.definition.code-required}")
    @Size(max = 64, message = "{validation.workflow.definition.code-size}")
    private String definitionCode;

    /** 委托人账号（必填） */
    @NotBlank(message = "{validation.workflow.delegate.delegator-required}")
    @Size(max = 64, message = "{validation.workflow.delegate.delegator-account-size}")
    private String fromAccount;

    /** 代理人账号（必填） */
    @NotBlank(message = "{validation.workflow.delegate.delegate-required}")
    @Size(max = 64, message = "{validation.workflow.delegate.delegate-account-size}")
    private String toAccount;

    /** 生效开始时间（必填） */
    @NotNull(message = "{validation.workflow.delegate.effective-start-required}")
    private LocalDateTime startTime;

    /** 生效结束时间（必填） */
    @NotNull(message = "{validation.workflow.delegate.effective-end-required}")
    private LocalDateTime endTime;

    /** 是否启用（可选，默认启用） */
    private Boolean enabled;

    /** 备注（可选，最长 255） */
    @Size(max = 255, message = "{validation.workflow.delegate.remark-size}")
    private String remark;
}
