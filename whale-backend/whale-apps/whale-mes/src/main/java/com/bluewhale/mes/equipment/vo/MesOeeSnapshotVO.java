package com.bluewhale.mes.equipment.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * OEE 快照视图
 *
 * @author BlueWhale
 */
@Data
public class MesOeeSnapshotVO {

    /** 快照 ID */
    private Long id;

    /** 快照编号 */
    private String snapshotNo;

    /** 设备 ID */
    private Long equipmentId;

    /** 设备编码 */
    private String equipmentCode;

    /** 班次日期 */
    private String shiftDate;

    /** 班次编码 */
    private String shiftCode;

    /** 计划生产时间（分钟） */
    private BigDecimal plannedMinutes;

    /** 计划停机时间（分钟） */
    private BigDecimal plannedDowntimeMinutes;

    /** 实际运行时间（分钟） */
    private BigDecimal runMinutes;

    /** 非计划停机时间（分钟） */
    private BigDecimal downtimeMinutes;

    /** 总产量 */
    private BigDecimal totalCount;

    /** 合格产量 */
    private BigDecimal goodCount;

    /** 理论节拍（秒/件） */
    private BigDecimal idealCycleSeconds;

    /** 可用率 */
    private BigDecimal availability;

    /** 性能 */
    private BigDecimal performance;

    /** 质量 */
    private BigDecimal quality;

    /** 综合效率 */
    private BigDecimal oee;

    /** 口径配置 */
    private String formulaConfig;

    /** 计算时间 */
    private LocalDateTime computedAt;
}
