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
    @NotBlank(message = "{validation.oa.leave-type.code-required}")
    @Pattern(regexp = "[A-Z][A-Z0-9_]*", message = "{validation.oa.leave-type.code-pattern}")
    @Size(max = 32, message = "{validation.oa.leave-type.code-size}")
    private String code;

    /** 名称 */
    @NotBlank(message = "{validation.oa.leave-type.name-required}")
    @Size(max = 64, message = "{validation.oa.leave-type.name-size}")
    private String name;

    /** 计量单位 */
    @NotBlank(message = "{validation.oa.leave-type.unit-required}")
    @Pattern(regexp = "DAY|HOUR", message = "{validation.oa.leave-type.unit-pattern}")
    private String unit;

    /** 年度默认额度 */
    @NotNull(message = "{validation.oa.leave-type.annual-quota-required}")
    @DecimalMin(value = "0.0", message = "{validation.oa.leave-type.annual-quota-min}")
    private BigDecimal annualQuota;

    /** 最小请假单位 */
    @NotNull(message = "{validation.oa.leave-type.min-unit-required}")
    @DecimalMin(value = "0.01", message = "{validation.oa.leave-type.min-unit-min}")
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
    @Size(max = 255, message = "{validation.oa.leave-type.remark-size}")
    private String remark;
}
