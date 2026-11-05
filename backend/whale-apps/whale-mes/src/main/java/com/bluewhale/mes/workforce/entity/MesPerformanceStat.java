package com.bluewhale.mes.workforce.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * MES 人员绩效统计实体，对应表 {@code mes_wf_performance_stat}。
 *
 * <p>日统计期间编码为 {@code YYYYMMDD}，月统计为 {@code YYYYMM} 且统计日期取期间首日；
 * 月度锁定后禁止任何重算或修改。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_wf_performance_stat")
public class MesPerformanceStat extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 统计层级：DAILY/MONTHLY */
    private String statLevel;

    /** 员工 ID */
    private Long employeeId;

    /** 员工姓名 */
    private String employeeName;

    /** 班组 ID */
    private Long teamId;

    /** 班组编码快照 */
    private String teamCode;

    /** 统计日期（月统计取期间首日） */
    private LocalDate workDate;

    /** 期间编码：日 YYYYMMDD，月 YYYYMM */
    private String periodCode;

    /** 产量 */
    private BigDecimal productionQuantity;

    /** 合格数量 */
    private BigDecimal qualifiedQuantity;

    /** 不良数量 */
    private BigDecimal defectQuantity;

    /** 净工时秒数 */
    private Long workSeconds;

    /** 标准工时秒数（工艺路线快照口径） */
    private Long standardSeconds;

    /** 效率：标准工时/净工时 */
    private BigDecimal efficiency;

    /** 合格率：合格/产量 */
    private BigDecimal passRate;

    /** 月度锁定：0 否 / 1 是 */
    private Integer locked;

    /** 锁定时间 */
    private LocalDateTime lockedAt;

    /** 锁定人 */
    private String lockedBy;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
