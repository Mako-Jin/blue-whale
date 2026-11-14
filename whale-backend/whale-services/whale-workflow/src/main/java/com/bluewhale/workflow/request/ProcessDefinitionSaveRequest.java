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
    @NotBlank(message = "流程编码不能为空")
    @Size(max = 64, message = "流程编码长度不能超过 64")
    private String code;

    /** 流程名称（必填，最长 128） */
    @NotBlank(message = "流程名称不能为空")
    @Size(max = 128, message = "流程名称长度不能超过 128")
    private String name;

    /** 流程定义（JSON 文本，节点与流转，最长 64KB；超限由服务层校验抛参数错误） */
    @NotBlank(message = "流程定义不能为空")
    private String definitionJson;

    /** 版本号；为空时新增取默认 1，更新时保持不变 */
    private Integer version;

    /** 状态：1 正常 / 0 禁用；为空时新增取默认 1 */
    private Integer status;

    /** 备注（最长 255） */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
