package com.bluewhale.form.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 字段选项视图对象（选项层）。
 *
 * @author BlueWhale
 */
@Data
public class FormFieldOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 表单字段配置ID */
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
