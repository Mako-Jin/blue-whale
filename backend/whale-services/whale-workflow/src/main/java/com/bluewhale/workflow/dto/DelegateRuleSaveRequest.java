package com.bluewhale.workflow.dto;

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
    @NotBlank(message = "流程编码不能为空")
    @Size(max = 64, message = "流程编码长度不能超过 64")
    private String definitionCode;

    /** 委托人账号（必填） */
    @NotBlank(message = "委托人账号不能为空")
    @Size(max = 64, message = "委托人账号长度不能超过 64")
    private String fromAccount;

    /** 代理人账号（必填） */
    @NotBlank(message = "代理人账号不能为空")
    @Size(max = 64, message = "代理人账号长度不能超过 64")
    private String toAccount;

    /** 生效开始时间（必填） */
    @NotNull(message = "生效开始时间不能为空")
    private LocalDateTime startTime;

    /** 生效结束时间（必填） */
    @NotNull(message = "生效结束时间不能为空")
    private LocalDateTime endTime;

    /** 是否启用（可选，默认启用） */
    private Boolean enabled;

    /** 备注（可选，最长 255） */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
