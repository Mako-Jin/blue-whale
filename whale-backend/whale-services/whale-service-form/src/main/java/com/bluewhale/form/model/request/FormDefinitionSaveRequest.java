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
    @NotBlank(message = "{validation.form.definition.code-required}")
    @Size(max = 40, message = "{validation.form.definition.code-size}")
    @Pattern(regexp = "^[a-z][a-z0-9_]{0,39}$",
            message = "{validation.form.definition.code-pattern}")
    private String code;

    /** 表单名称（必填，最长 128） */
    @NotBlank(message = "{validation.form.definition.name-required}")
    @Size(max = 128, message = "{validation.form.definition.name-size}")
    private String name;

    /** 所属表定义 ID（元数据模型；为空则按旧模型以 schemaJson 为准） */
    private Long tableDefinitionId;

    /** 字段配置集合（元数据模型；整体替换，服务层据此编译 schemaJson 快照） */
    @Valid
    private List<FormFieldConfigDTO> fields;

    /** 表单结构定义（JSON 文本，最长 64KB；旧模型直接使用，元数据模型下被编译快照覆盖） */
    @Size(max = 65536, message = "{validation.form.definition.schema-size}")
    private String schemaJson;

    /** 版本号；为空时新增取默认 1，更新时保持不变（只能递增） */
    @Min(value = 1, message = "{validation.form.definition.version-min}")
    private Integer version;

    /** 状态：1 正常 / 0 禁用；为空时新增取默认 1 */
    @Min(value = 0, message = "{validation.form.definition.status-range}")
    @Max(value = 1, message = "{validation.form.definition.status-range}")
    private Integer status;

    /** 备注（最长 255） */
    @Size(max = 255, message = "{validation.form.definition.remark-size}")
    private String remark;

    /** 表单分类（最长 64；空白视为不分类） */
    @Size(max = 64, message = "{validation.form.definition.category-size}")
    private String category;

    /** 标签（JSON 字符串数组文本，如 {@code ["报销","财务"]}，最长 512；空白视为无标签） */
    @Size(max = 512, message = "{validation.form.definition.tags-size}")
    private String tags;
}
