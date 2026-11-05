package com.bluewhale.oa.hr.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 假期类型新增 / 更新命令。
 *
 * @author BlueWhale
 */
@Data
public class LeaveTypeSaveCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 编码 */
    @NotBlank(message = "假期类型编码不能为空")
    @Pattern(regexp = "[A-Z][A-Z0-9_]*", message = "假期类型编码须以大写字母开头，仅含大写字母/数字/下划线")
    @Size(max = 32, message = "假期类型编码长度不能超过 32")
    private String code;

    /** 名称 */
    @NotBlank(message = "假期类型名称不能为空")
    @Size(max = 64, message = "假期类型名称长度不能超过 64")
    private String name;

    /** 计量单位 */
    @NotBlank(message = "假期计量单位不能为空")
    @Pattern(regexp = "DAY|HOUR", message = "假期计量单位仅支持 DAY / HOUR")
    private String unit;

    /** 年度默认额度 */
    @NotNull(message = "年度默认额度不能为空")
    @DecimalMin(value = "0.0", message = "年度默认额度不能为负数")
    private BigDecimal annualQuota;

    /** 最小请假单位 */
    @NotNull(message = "最小请假单位不能为空")
    @DecimalMin(value = "0.01", message = "最小请假单位必须大于 0")
    private BigDecimal minUnit;

    /** 是否必须上传附件 */
    private Boolean requiresAttachment;

    /** 是否允许负额度 */
    private Boolean allowNegative;

    /** 是否启用 */
    private Boolean enabled;

    /** 排序号 */
    private Integer sortNo;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
