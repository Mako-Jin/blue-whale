package com.bluewhale.form.model.request;

import com.bluewhale.form.model.dto.FormFieldConfigDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

    /** 表单编码（必填，小写字母开头，仅字母/数字/下划线，最长 40） */
    @NotBlank(message = "表单编码不能为空")
    @Size(max = 40, message = "表单编码长度不能超过 40")
    @Pattern(regexp = "^[a-z][a-z0-9_]{0,39}$",
            message = "表单编码仅允许小写字母、数字、下划线且以字母开头，长度不超过 40")
    private String code;

    /** 表单名称（必填，最长 128） */
    @NotBlank(message = "表单名称不能为空")
    @Size(max = 128, message = "表单名称长度不能超过 128")
    private String name;

    /** 所属表定义 ID（元数据模型；为空则按旧模型以 schemaJson 为准） */
    private Long tableDefinitionId;

    /** 字段配置集合（元数据模型；整体替换，服务层据此编译 schemaJson 快照） */
    @Valid
    private List<FormFieldConfigDTO> fields;

    /** 表单结构定义（JSON 文本，最长 64KB；旧模型直接使用，元数据模型下被编译快照覆盖） */
    @Size(max = 65536, message = "表单结构定义长度不能超过 65536")
    private String schemaJson;

    /** 版本号；为空时新增取默认 1，更新时保持不变（只能递增） */
    @Min(value = 1, message = "版本号不能小于 1")
    private Integer version;

    /** 状态：1 正常 / 0 禁用；为空时新增取默认 1 */
    @Min(value = 0, message = "状态仅允许 0 或 1")
    @Max(value = 1, message = "状态仅允许 0 或 1")
    private Integer status;

    /** 备注（最长 255） */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;

    /** 表单分类（最长 64；空白视为不分类） */
    @Size(max = 64, message = "表单分类长度不能超过 64")
    private String category;

    /** 标签（JSON 字符串数组文本，如 {@code ["报销","财务"]}，最长 512；空白视为无标签） */
    @Size(max = 512, message = "标签长度不能超过 512")
    private String tags;
}
