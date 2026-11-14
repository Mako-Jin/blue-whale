package com.bluewhale.form.dto;

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
    @NotBlank(message = "选项显示名不能为空")
    @Size(max = 128, message = "选项显示名长度不能超过 128")
    private String optionLabel;

    /** 选项值（最长 128） */
    @NotBlank(message = "选项值不能为空")
    @Size(max = 128, message = "选项值长度不能超过 128")
    private String optionValue;

    /** 排序号 */
    @Min(value = 0, message = "排序号不能小于 0")
    private Integer sortNo;
}
