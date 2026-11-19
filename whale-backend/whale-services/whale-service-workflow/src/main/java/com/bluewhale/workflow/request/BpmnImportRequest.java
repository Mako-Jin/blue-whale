package com.bluewhale.workflow.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * BPMN 导入请求：以 JSON 字符串字段承载 XML（避免请求体走 XML 反序列化，规避 XXE）。
 *
 * @author BlueWhale
 */
@Data
public class BpmnImportRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流程编码（必填，最长 64） */
    @NotBlank(message = "{validation.workflow.definition.code-required}")
    @Size(max = 64, message = "{validation.workflow.definition.code-size}")
    private String code;

    /** 流程名称（必填，最长 128） */
    @NotBlank(message = "{validation.workflow.definition.name-required}")
    @Size(max = 128, message = "{validation.workflow.definition.name-size}")
    private String name;

    /** BPMN 2.0 XML 文本（必填） */
    @NotBlank(message = "{validation.workflow.definition.bpmn-xml-required}")
    private String bpmnXml;

    /** 版本号；为空取默认 1 */
    private Integer version;

    /** 状态：1 正常 / 0 禁用；为空取默认 1 */
    private Integer status;

    /** 备注（最长 255） */
    @Size(max = 255, message = "{validation.workflow.definition.remark-size}")
    private String remark;
}
