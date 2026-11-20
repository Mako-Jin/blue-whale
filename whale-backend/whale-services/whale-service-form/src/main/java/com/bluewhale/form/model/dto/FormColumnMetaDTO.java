package com.bluewhale.form.model.dto;

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
    @NotBlank(message = "{validation.form.column.code-required}")
    @Size(max = 64, message = "{validation.form.column.code-size}")
    @Pattern(regexp = "^[a-z][a-z0-9_]{0,63}$",
            message = "{validation.form.column.code-pattern}")
    private String columnCode;

    /** 列名称（业务名，最长 128） */
    @Size(max = 128, message = "{validation.form.column.name-size}")
    private String columnName;

    /** 存储类型：text / textarea / number / date / datetime / select / bool / attachment / subtable */
    @NotBlank(message = "{validation.form.column.data-type-required}")
    @Size(max = 32, message = "{validation.form.column.data-type-size}")
    private String dataType;

    /** 长度（text 生效，范围 1..2000；缺省 255） */
    @Min(value = 1, message = "{validation.form.column.length-min}")
    @Max(value = 2000, message = "{validation.form.column.length-max}")
    private Integer length;

    /** 精度（number 生效，范围 1..65；缺省 18） */
    @Min(value = 1, message = "{validation.form.column.precision-min}")
    @Max(value = 65, message = "{validation.form.column.precision-max}")
    private Integer precision;

    /** 小数位（number 生效，范围 0..30；缺省 4） */
    @Min(value = 0, message = "{validation.form.column.scale-min}")
    @Max(value = 30, message = "{validation.form.column.scale-max}")
    private Integer scale;

    /** 能否为空：1 可空 / 0 非空；必填列必须为 0 */
    @Min(value = 0, message = "{validation.form.column.nullable-range}")
    @Max(value = 1, message = "{validation.form.column.nullable-range}")
    private Integer nullable;

    /** 是否必填：1 必填 / 0 非必填；缺省 0 */
    @Min(value = 0, message = "{validation.form.column.required-range}")
    @Max(value = 1, message = "{validation.form.column.required-range}")
    private Integer required;

    /** 敏感字段：1 敏感 / 0 普通；缺省 0，仅影响导出脱敏 */
    @Min(value = 0, message = "{validation.form.column.sensitive-range}")
    @Max(value = 1, message = "{validation.form.column.sensitive-range}")
    private Integer sensitive;

    /** 默认值 */
    @Size(max = 255, message = "{validation.form.column.default-value-size}")
    private String defaultValue;

    /** 备注 */
    @Size(max = 255, message = "{validation.form.column.remark-size}")
    private String remark;

    /** 排序号 */
    @Min(value = 0, message = "{validation.form.column.sort-no-min}")
    private Integer sortNo;

    /** 状态：1 正常 / 0 禁用；缺省 1 */
    @Min(value = 0, message = "{validation.form.column.status-range}")
    @Max(value = 1, message = "{validation.form.column.status-range}")
    private Integer status;
}
