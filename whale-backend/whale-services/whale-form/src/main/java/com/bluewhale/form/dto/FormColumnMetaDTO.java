package com.bluewhale.form.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 列元数据请求（表定义层 · 动态列详情）。
 *
 * @author BlueWhale
 */
@Data
public class FormColumnMetaDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 列编码（物理列名；小写字母开头，仅字母/数字/下划线，最长 64） */
    @NotBlank(message = "列编码不能为空")
    @Size(max = 64, message = "列编码长度不能超过 64")
    @Pattern(regexp = "^[a-z][a-z0-9_]{0,63}$",
            message = "列编码仅允许小写字母、数字、下划线且以字母开头，长度不超过 64")
    private String columnCode;

    /** 列名称（业务名，最长 128） */
    @Size(max = 128, message = "列名称长度不能超过 128")
    private String columnName;

    /** 存储类型：text / textarea / number / date / datetime / select / bool / attachment / subtable */
    @NotBlank(message = "列存储类型不能为空")
    @Size(max = 32, message = "列存储类型长度不能超过 32")
    private String dataType;

    /** 长度（text 生效，范围 1..2000；缺省 255） */
    @Min(value = 1, message = "列长度不能小于 1")
    @Max(value = 2000, message = "列长度不能超过 2000")
    private Integer length;

    /** 精度（number 生效，范围 1..65；缺省 18） */
    @Min(value = 1, message = "列精度不能小于 1")
    @Max(value = 65, message = "列精度不能超过 65")
    private Integer precision;

    /** 小数位（number 生效，范围 0..30；缺省 4） */
    @Min(value = 0, message = "列小数位不能小于 0")
    @Max(value = 30, message = "列小数位不能超过 30")
    private Integer scale;

    /** 能否为空：1 可空 / 0 非空；必填列必须为 0 */
    @Min(value = 0, message = "nullable 仅允许 0 或 1")
    @Max(value = 1, message = "nullable 仅允许 0 或 1")
    private Integer nullable;

    /** 是否必填：1 必填 / 0 非必填；缺省 0 */
    @Min(value = 0, message = "required 仅允许 0 或 1")
    @Max(value = 1, message = "required 仅允许 0 或 1")
    private Integer required;

    /** 默认值 */
    @Size(max = 255, message = "默认值长度不能超过 255")
    private String defaultValue;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;

    /** 排序号 */
    @Min(value = 0, message = "排序号不能小于 0")
    private Integer sortNo;

    /** 状态：1 正常 / 0 禁用；缺省 1 */
    @Min(value = 0, message = "状态仅允许 0 或 1")
    @Max(value = 1, message = "状态仅允许 0 或 1")
    private Integer status;
}
