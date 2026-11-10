package com.bluewhale.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 流程定义选路模拟请求：以真实条件求值预演流程走向。
 *
 * @author BlueWhale
 */
@Data
public class DefinitionSimulateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流程定义 JSON 文本（规范化或 legacy） */
    @NotBlank(message = "definitionJson 不能为空")
    private String definitionJson;

    /** 模拟用流程变量（条件表达式求值上下文，可空） */
    private Map<String, Object> variables;
}
