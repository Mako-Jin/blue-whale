package com.bluewhale.form.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 表单字段配置请求（表单视图层 · 字段属性详情，含选项）。
 *
 * @author BlueWhale
 */
@Data
public class FormFieldConfigDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 关联列元数据 ID；{@code null} 为纯布局字段（标题/分割线/说明） */
    private Long columnMetaId;

    /** 字段显示名（最长 128） */
    @NotBlank(message = "字段显示名不能为空")
    @Size(max = 128, message = "字段显示名长度不能超过 128")
    private String fieldLabel;

    /** 控件类型（缺省按存储类型回落，最长 32） */
    @Size(max = 32, message = "控件类型长度不能超过 32")
    private String widgetType;

    /** 列表是否显示：1 显示 / 0 隐藏；缺省 1 */
    @Min(value = 0, message = "showInList 仅允许 0 或 1")
    @Max(value = 1, message = "showInList 仅允许 0 或 1")
    private Integer showInList;

    /** 表单是否显示：1 显示 / 0 隐藏；缺省 1 */
    @Min(value = 0, message = "showInForm 仅允许 0 或 1")
    @Max(value = 1, message = "showInForm 仅允许 0 或 1")
    private Integer showInForm;

    /** 是否允许编辑：1 可编辑 / 0 只读；缺省 1 */
    @Min(value = 0, message = "editable 仅允许 0 或 1")
    @Max(value = 1, message = "editable 仅允许 0 或 1")
    private Integer editable;

    /** 排序号 */
    @Min(value = 0, message = "排序号不能小于 0")
    private Integer sortNo;

    /** 栅格宽度：12 半宽 / 24 整宽；缺省 24 */
    @Min(value = 1, message = "栅格宽度不能小于 1")
    @Max(value = 24, message = "栅格宽度不能超过 24")
    private Integer colSpan;

    /** 占位提示 */
    @Size(max = 255, message = "占位提示长度不能超过 255")
    private String placeholder;

    /** 默认值（视图级覆盖） */
    @Size(max = 255, message = "默认值长度不能超过 255")
    private String defaultValue;

    /** 校验规则 JSON */
    @Size(max = 1024, message = "校验规则长度不能超过 1024")
    private String validateJson;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;

    /** 状态：1 正常 / 0 禁用；缺省 1 */
    @Min(value = 0, message = "状态仅允许 0 或 1")
    @Max(value = 1, message = "状态仅允许 0 或 1")
    private Integer status;

    /** 选项列表（下拉/单选/多选） */
    @Valid
    private List<FormFieldOptionDTO> options;
}
