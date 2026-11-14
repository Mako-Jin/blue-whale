package com.bluewhale.workflow.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import org.springframework.util.StringUtils;

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

    /**
     * 校验两个定义来源至少提供一个（类级约束）。
     *
     * @return 至少一个非空返回 {@code true}
     */
    @JsonIgnore
    @AssertTrue(message = "流程定义 JSON 与 BPMN XML 不能同时为空")
    public boolean isDefinitionPresent() {
        return StringUtils.hasText(definitionJson) || StringUtils.hasText(bpmnXml);
    }
}
