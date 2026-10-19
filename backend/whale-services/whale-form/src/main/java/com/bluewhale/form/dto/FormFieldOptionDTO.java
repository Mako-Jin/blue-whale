package com.bluewhale.form.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 字段选项请求（选项层）。
 *
 * @author BlueWhale
 */
@Data
public class FormFieldOptionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 选项显示名 */
    private String optionLabel;

    /** 选项值 */
    private String optionValue;

    /** 排序号 */
    private Integer sortNo;
}
