package com.bluewhale.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 发起流程实例请求。
 *
 * @author BlueWhale
 */
@Data
public class ProcessInstanceStartRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流程定义 ID（必填） */
    @NotNull(message = "流程定义ID不能为空")
    private Long definitionId;

    /** 实例标题（必填，最长 128） */
    @NotBlank(message = "实例标题不能为空")
    @Size(max = 128, message = "实例标题长度不能超过 128")
    private String title;

    /** 业务键（可选，最长 64） */
    @Size(max = 64, message = "业务键长度不能超过 64")
    private String businessKey;

    /** 流程变量（可选，用于条件选路；随实例落库，最长 64KB） */
    private Map<String, Object> variables;

    /** 审批人覆盖（可选，nodeKey → 账号列表；只允许用户任务/抄送节点，单节点最多 20 人） */
    private Map<String, List<String>> assigneeOverrides;

    /** 发起级抄送人用户ID（可选，最多 100 个；只发通知，不产生待办） */
    @Size(max = 100, message = "发起级抄送人不能超过 100 个")
    private List<Long> ccUserIds;
}
