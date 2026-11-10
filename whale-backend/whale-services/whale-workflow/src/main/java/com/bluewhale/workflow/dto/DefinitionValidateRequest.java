package com.bluewhale.workflow.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 流程定义校验请求：{@code definitionJson} 与 {@code bpmnXml} 二选一。
 *
 * @author BlueWhale
 */
@Data
public class DefinitionValidateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流程定义 JSON 文本（规范化或 legacy） */
    private String definitionJson;

    /** BPMN 2.0 XML 文本 */
    private String bpmnXml;
}
