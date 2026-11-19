package com.bluewhale.workflow.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 内网发起流程实例请求（OA 按流程编码发起）。
 *
 * @author BlueWhale
 */
@Data
public class InternalProcessStartRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流程编码（必填，最长 64） */
    @NotBlank(message = "{validation.workflow.definition.code-required}")
    @Size(max = 64, message = "{validation.workflow.definition.code-size}")
    private String definitionCode;

    /** 实例标题（必填，最长 128） */
    @NotBlank(message = "{validation.workflow.instance.title-required}")
    @Size(max = 128, message = "{validation.workflow.instance.title-size}")
    private String title;

    /** 业务键（可选，最长 64） */
    @Size(max = 64, message = "{validation.workflow.instance.business-key-size}")
    private String businessKey;

    /** 发起人账号（可选，最长 64；为空时由网关头补齐） */
    @Size(max = 64, message = "{validation.workflow.instance.initiator-account-size}")
    private String initiator;

    /** 流程变量（可选，用于条件选路） */
    private Map<String, Object> variables;

    /** 审批人覆盖（可选，nodeKey → 账号列表） */
    private Map<String, List<String>> assigneeOverrides;

    /** 发起级抄送人账号（可选，最多 100 个） */
    @Size(max = 100, message = "{validation.workflow.instance.cc-limit}")
    private List<@Size(max = 64, message = "{validation.workflow.instance.cc-account-size}") String> ccAccounts;
}
