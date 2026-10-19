package com.bluewhale.form.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表单字段配置实体，对应表 {@code form_field_config}（三层模型的表单视图层·字段属性详情）。
 *
 * <p>描述某张表单视图如何呈现某个列（或纯布局字段）：显示名、<b>控件类型</b>{@code widgetType}、
 * 列表/表单是否显示、是否可编辑、排序、占位、默认值、校验规则；通过 {@code columnMetaId}
 * 关联到表定义层的列（为 {@code null} 时表示标题/分割线/说明等纯布局字段）。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("form_field_config")
public class FormFieldConfig extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 表单定义 ID */
    private Long formId;

    /** 关联列元数据 ID；{@code null} 为纯布局字段（标题/分割线/说明） */
    private Long columnMetaId;

    /** 字段显示名 */
    private String fieldLabel;

    /** 控件类型：text / textarea / number / select / radio / checkbox / date / datetime / switch */
    private String widgetType;

    /** 列表是否显示：1 显示 / 0 隐藏 */
    private Integer showInList;

    /** 表单是否显示：1 显示 / 0 隐藏 */
    private Integer showInForm;

    /** 是否允许编辑：1 可编辑 / 0 只读 */
    private Integer editable;

    /** 排序号 */
    private Integer sortNo;

    /** 占位提示 */
    private String placeholder;

    /** 默认值（视图级覆盖） */
    private String defaultValue;

    /** 校验规则 JSON（如 {@code {"min":0,"max":150}}） */
    private String validateJson;

    /** 备注 */
    private String remark;

    /** 状态：1 正常 / 0 禁用 */
    private Integer status;
}
