package com.bluewhale.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

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
}
