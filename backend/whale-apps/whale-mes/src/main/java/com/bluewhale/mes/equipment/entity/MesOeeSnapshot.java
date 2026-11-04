package com.bluewhale.mes.equipment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * OEE 快照实体，对应表 {@code mes_eq_oee_snapshot}。
 *
 * <p>按设备 + 班次唯一。默认口径：可用率 = 运行 / (计划 − 计划停机)、
 * 性能 = 理论产量 / 运行时间、质量 = 合格 / 总产量、OEE = 三者乘积。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_eq_oee_snapshot")
public class MesOeeSnapshot extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 快照编号（租户内唯一） */
    private String snapshotNo;

    /** 设备 ID */
    private Long equipmentId;

    /** 设备编码快照 */
    private String equipmentCode;

    /** 班次日期 */
    private LocalDate shiftDate;

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

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
