package com.bluewhale.form.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 表单字段配置视图对象（表单视图层 · 字段属性详情，含选项）。
 *
 * @author BlueWhale
 */
@Data
public class FormFieldConfigVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 表单定义ID */
    private Long formId;

    /** 关联列元数据ID；{@code null} 为纯布局字段 */
    private Long columnMetaId;

    /** 字段显示名 */
    private String fieldLabel;

    /** 控件类型 */
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

    /** 默认值 */
    private String defaultValue;

    /** 校验规则 JSON */
    private String validateJson;

    /** 备注 */
    private String remark;

    /** 状态：1 正常 / 0 禁用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 选项集合 */
    private List<FormFieldOptionVO> options;
}
