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
    @NotBlank(message = "流程编码不能为空")
    @Size(max = 64, message = "流程编码长度不能超过 64")
    private String code;

    /** 流程名称（必填） */
    @NotBlank(message = "流程名称不能为空")
    @Size(max = 128, message = "流程名称长度不能超过 128")
    private String name;

    /** 流程定义 JSON（必填） */
    @NotBlank(message = "流程定义不能为空")
    private String definitionJson;

    /** 备注（可选） */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
