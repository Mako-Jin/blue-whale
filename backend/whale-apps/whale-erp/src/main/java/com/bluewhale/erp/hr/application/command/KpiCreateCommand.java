package com.bluewhale.erp.hr.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增绩效指标命令。
 *
 * @author BlueWhale
 */
@Data
public class KpiCreateCommand {

    /** 指标名称 */
    @NotBlank
    private String name;

    /** 权重（百分比） */
    private BigDecimal weight;

    /** 目标值 */
    private BigDecimal targetValue;

    /** 计量单位 */
    private String unit;

    /** 备注 */
    private String remark;
}
