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
    @NotNull(message = "{validation.mes.mes-oee-snapshot.equipment-id-required}")
    private Long equipmentId;

    /** 班次日期 */
    @NotNull(message = "{validation.mes.mes-oee-snapshot.shift-date-required}")
    private LocalDate shiftDate;

    /** 班次编码 */
    @NotBlank(message = "{validation.mes.mes-oee-snapshot.shift-code-required}")
    private String shiftCode;

    /** 计划生产时间（分钟） */
    @NotNull(message = "{validation.mes.mes-oee-snapshot.planned-minutes-required}")
    @DecimalMin(value = "0.0001", message = "{validation.mes.mes-oee-snapshot.planned-minutes-min}")
    private BigDecimal plannedMinutes;

    /** 计划停机时间（分钟） */
    @DecimalMin(value = "0.0000", message = "{validation.mes.mes-oee-snapshot.planned-downtime-minutes-min}")
    private BigDecimal plannedDowntimeMinutes;

    /** 实际运行时间（分钟） */
    @NotNull(message = "{validation.mes.mes-oee-snapshot.run-minutes-required}")
    @DecimalMin(value = "0.0000", message = "{validation.mes.mes-oee-snapshot.run-minutes-min}")
    private BigDecimal runMinutes;

    /** 非计划停机时间（分钟） */
    @DecimalMin(value = "0.0000", message = "{validation.mes.mes-oee-snapshot.downtime-minutes-min}")
    private BigDecimal downtimeMinutes;

    /** 总产量 */
    @NotNull(message = "{validation.mes.mes-oee-snapshot.total-count-required}")
    @DecimalMin(value = "0.0000", message = "{validation.mes.mes-oee-snapshot.total-count-min}")
    private BigDecimal totalCount;

    /** 合格产量 */
    @NotNull(message = "{validation.mes.mes-oee-snapshot.good-count-required}")
    @DecimalMin(value = "0.0000", message = "{validation.mes.mes-oee-snapshot.good-count-min}")
    private BigDecimal goodCount;

    /** 理论节拍（秒/件） */
    @NotNull(message = "{validation.mes.mes-oee-snapshot.ideal-cycle-seconds-required}")
    @DecimalMin(value = "0.0001", message = "{validation.mes.mes-oee-snapshot.ideal-cycle-seconds-min}")
    private BigDecimal idealCycleSeconds;

    /** 口径配置（可覆盖默认公式） */
    private String formulaConfig;

    /** 备注 */
    private String remark;
}
