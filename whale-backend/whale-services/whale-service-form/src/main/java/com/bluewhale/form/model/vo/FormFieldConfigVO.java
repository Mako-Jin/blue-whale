package com.bluewhale.form.model.vo;

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

    /** 栅格宽度：12 半宽 / 24 整宽 */
    private Integer colSpan;

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

    /** 关联列编码（关联列信息冗余回填；{@code columnMetaId == null} 的纯布局字段为 {@code null}） */
    private String columnCode;

    /** 关联列存储类型（关联列信息冗余回填） */
    private String dataType;

    /** 关联列是否必填：1 必填 / 0 非必填（关联列信息冗余回填） */
    private Integer required;

    /** 关联列长度（关联列信息冗余回填，供前端 maxLength 提示） */
    private Integer maxLength;

    /** 选项集合 */
    private List<FormFieldOptionVO> options;
}
