package com.bluewhale.workflow.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 新增/更新流程定义请求。
 *
 * @author BlueWhale
 */
@Data
public class ProcessDefinitionSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流程编码（必填，最长 64） */
    @NotBlank(message = "{validation.workflow.definition.code-required}")
    @Size(max = 64, message = "{validation.workflow.definition.code-size}")
    private String code;

    /** 流程名称（必填，最长 128） */
    @NotBlank(message = "{validation.workflow.definition.name-required}")
    @Size(max = 128, message = "{validation.workflow.definition.name-size}")
    private String name;

    /** 流程定义（JSON 文本，节点与流转，最长 64KB；超限由服务层校验抛参数错误） */
    @NotBlank(message = "{validation.workflow.definition.content-required}")
    private String definitionJson;

    /** 版本号；为空时新增取默认 1，更新时保持不变 */
    private Integer version;

    /** 状态：1 正常 / 0 禁用；为空时新增取默认 1 */
    private Integer status;

    /** 备注（最长 255） */
    @Size(max = 255, message = "{validation.workflow.definition.remark-size}")
    private String remark;
}
