package com.bluewhale.form.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    /** 选项显示名（最长 128） */
    @NotBlank(message = "{validation.form.option.label-required}")
    @Size(max = 128, message = "{validation.form.option.label-size}")
    private String optionLabel;

    /** 选项值（最长 128） */
    @NotBlank(message = "{validation.form.option.value-required}")
    @Size(max = 128, message = "{validation.form.option.value-size}")
    private String optionValue;

    /** 排序号 */
    @Min(value = 0, message = "{validation.form.option.sort-no-min}")
    private Integer sortNo;
}
