package com.bluewhale.mes.equipment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * OEE 快照计算请求（按设备 + 班次）
 *
 * <p>默认口径：可用率 = 运行 / (计划 − 计划停机)；性能 = 理论产量 / 运行；
 * 质量 = 合格 / 总产量；OEE = 三者乘积。计划停机单列，不计入可用率分母。</p>
 *
 * @author BlueWhale
 */
@Data
public class MesOeeSnapshotRequest {

    /** 设备 ID */
    @NotNull(message = "设备不能为空")
    private Long equipmentId;

    /** 班次日期 */
    @NotNull(message = "班次日期不能为空")
    private LocalDate shiftDate;

    /** 班次编码 */
    @NotBlank(message = "班次编码不能为空")
    private String shiftCode;

    /** 计划生产时间（分钟） */
    @NotNull(message = "计划生产时间不能为空")
    @DecimalMin(value = "0.0001", message = "计划生产时间必须大于 0")
    private BigDecimal plannedMinutes;

    /** 计划停机时间（分钟） */
    @DecimalMin(value = "0.0000", message = "计划停机时间不能为负")
    private BigDecimal plannedDowntimeMinutes;

    /** 实际运行时间（分钟） */
    @NotNull(message = "实际运行时间不能为空")
    @DecimalMin(value = "0.0000", message = "实际运行时间不能为负")
    private BigDecimal runMinutes;

    /** 非计划停机时间（分钟） */
    @DecimalMin(value = "0.0000", message = "非计划停机时间不能为负")
    private BigDecimal downtimeMinutes;

    /** 总产量 */
    @NotNull(message = "总产量不能为空")
    @DecimalMin(value = "0.0000", message = "总产量不能为负")
    private BigDecimal totalCount;

    /** 合格产量 */
    @NotNull(message = "合格产量不能为空")
    @DecimalMin(value = "0.0000", message = "合格产量不能为负")
    private BigDecimal goodCount;

    /** 理论节拍（秒/件） */
    @NotNull(message = "理论节拍不能为空")
    @DecimalMin(value = "0.0001", message = "理论节拍必须大于 0")
    private BigDecimal idealCycleSeconds;

    /** 口径配置（可覆盖默认公式） */
    private String formulaConfig;

    /** 备注 */
    private String remark;
}
