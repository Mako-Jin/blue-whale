package com.bluewhale.form.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 新增/更新表单定义请求。
 *
 * @author BlueWhale
 */
@Data
public class FormDefinitionSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 表单编码（必填，最长 64） */
    @NotBlank(message = "表单编码不能为空")
    @Size(max = 64, message = "表单编码长度不能超过 64")
    private String code;

    /** 表单名称（必填，最长 128） */
    @NotBlank(message = "表单名称不能为空")
    @Size(max = 128, message = "表单名称长度不能超过 128")
    private String name;

    /** 所属表定义 ID（元数据模型；为空则按旧模型以 schemaJson 为准） */
    private Long tableDefinitionId;

    /** 字段配置集合（元数据模型；整体替换，服务层据此编译 schemaJson 快照） */
    private List<FormFieldConfigDTO> fields;

    /** 表单结构定义（JSON 文本，最长 64KB；旧模型直接使用，元数据模型下被编译快照覆盖） */
    private String schemaJson;

    /** 版本号；为空时新增取默认 1，更新时保持不变 */
    private Integer version;

    /** 状态：1 正常 / 0 禁用；为空时新增取默认 1 */
    private Integer status;

    /** 备注（最长 255） */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
