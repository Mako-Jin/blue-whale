package com.bluewhale.form.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字段选项实体，对应表 {@code form_field_option}（三层模型的选项层）。
 *
 * <p>下拉/单选/多选字段的候选值：显示名 {@code optionLabel} + 值 {@code optionValue} + 排序。
 * 独立成表以便按值检索与复用，不塞入字段配置的 JSON 列。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("form_field_option")
public class FormFieldOptionPo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 表单字段配置 ID */
    private Long fieldConfigId;

    /** 选项显示名 */
    private String optionLabel;

    /** 选项值 */
    private String optionValue;

    /** 排序号 */
    private Integer sortNo;

    /** 状态：1 正常 / 0 禁用 */
    private Integer status;
}
