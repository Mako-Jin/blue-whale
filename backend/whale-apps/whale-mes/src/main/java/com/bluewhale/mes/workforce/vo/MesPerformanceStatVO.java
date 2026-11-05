package com.bluewhale.mes.workforce.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 人员绩效统计视图
 *
 * @author BlueWhale
 */
@Data
public class MesPerformanceStatVO {

    /** 统计 ID */
    private Long id;

    /** 统计层级：DAILY/MONTHLY */
    private String statLevel;

    /** 员工 ID */
    private Long employeeId;

    /** 员工姓名 */
    private String employeeName;

    /** 班组 ID */
    private Long teamId;

    /** 班组编码 */
    private String teamCode;

    /** 统计日期 */
    private LocalDate workDate;

    /** 期间编码 */
    private String periodCode;

    /** 产量 */
    private BigDecimal productionQuantity;

    /** 合格数量 */
    private BigDecimal qualifiedQuantity;

    /** 不良数量 */
    private BigDecimal defectQuantity;

    /** 净工时秒数 */
    private Long workSeconds;

    /** 标准工时秒数 */
    private Long standardSeconds;

    /** 效率 */
    private BigDecimal efficiency;

    /** 合格率 */
    private BigDecimal passRate;

    /** 月度锁定：0 否 / 1 是 */
    private Integer locked;

    /** 锁定时间 */
    private LocalDateTime lockedAt;

    /** 锁定人 */
    private String lockedBy;

    /** 创建时间 */
    private LocalDateTime createTime;
}
