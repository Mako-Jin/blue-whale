package com.bluewhale.workflow.dto;

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
    @NotBlank(message = "流程编码不能为空")
    @Size(max = 64, message = "流程编码长度不能超过 64")
    private String code;

    /** 流程名称（必填，最长 128） */
    @NotBlank(message = "流程名称不能为空")
    @Size(max = 128, message = "流程名称长度不能超过 128")
    private String name;

    /** BPMN 2.0 XML 文本（必填） */
    @NotBlank(message = "BPMN XML 不能为空")
    private String bpmnXml;

    /** 版本号；为空取默认 1 */
    private Integer version;

    /** 状态：1 正常 / 0 禁用；为空取默认 1 */
    private Integer status;

    /** 备注（最长 255） */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
