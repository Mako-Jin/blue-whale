package com.bluewhale.form.dto;

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

    /** 字段显示名 */
    private String fieldLabel;

    /** 控件类型（缺省按存储类型回落） */
    private String widgetType;

    /** 列表是否显示：1 显示 / 0 隐藏；缺省 1 */
    private Integer showInList;

    /** 表单是否显示：1 显示 / 0 隐藏；缺省 1 */
    private Integer showInForm;

    /** 是否允许编辑：1 可编辑 / 0 只读；缺省 1 */
    private Integer editable;

    /** 排序号 */
    private Integer sortNo;

    /** 占位提示 */
    private String placeholder;

    /** 默认值（视图级覆盖） */
    private String defaultValue;

    /** 校验规则 JSON */
    private String validateJson;

    /** 备注 */
    private String remark;

    /** 状态：1 正常 / 0 禁用；缺省 1 */
    private Integer status;

    /** 选项列表（下拉/单选/多选） */
    private List<FormFieldOptionDTO> options;
}
