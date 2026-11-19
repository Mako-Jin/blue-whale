package com.bluewhale.workflow.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 流程定义 ensure 请求（内网种子使用）。
 *
 * @author BlueWhale
 */
@Data
public class DefinitionEnsureRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流程编码（必填，唯一） */
    @NotBlank(message = "{validation.workflow.definition.code-required}")
    @Size(max = 64, message = "{validation.workflow.definition.code-size}")
    private String code;

    /** 流程名称（必填） */
    @NotBlank(message = "{validation.workflow.definition.name-required}")
    @Size(max = 128, message = "{validation.workflow.definition.name-size}")
    private String name;

    /** 流程定义 JSON（必填） */
    @NotBlank(message = "{validation.workflow.definition.content-required}")
    private String definitionJson;

    /** 备注（可选） */
    @Size(max = 255, message = "{validation.workflow.definition.remark-size}")
    private String remark;
}
