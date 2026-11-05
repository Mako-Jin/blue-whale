package com.bluewhale.mes.dashboard.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 综合看板 - 人员域聚合视图
 *
 * <p>工时口径与人员绩效一致：净工时 = 结束 - 开始 - 暂停，仅统计 {@code VALID} 记录；
 * 月度统计取当月 {@code MONTHLY} 绩效快照，锁定数用于监控月度锁定进度。</p>
 *
 * @author BlueWhale
 */
@Data
public class MesWorkforceBoardVO {

    /** 当日报工次数（当日报工事实条数） */
    private Long todayReportCount;

    /** 当日有效工时记录数 */
    private Long todayWorkHourCount;

    /** 当日净工时合计（秒） */
    private Long todayNetWorkSeconds;

    /** 当日有效排班数（ASSIGNED） */
    private Long activeShiftCount;

    /** 未确认交接单数（DRAFT） */
    private Long draftHandoverCount;

    /** 当月月度绩效统计条数 */
    private Long monthlyStatCount;

    /** 当月已锁定月度绩效条数 */
    private Long monthlyLockedCount;

    /** 当月产量合计 */
    private BigDecimal monthlyProductionQuantity;

    /** 当月平均效率（4 位小数，无数据为空） */
    private BigDecimal monthlyAvgEfficiency;

    /** 当月平均合格率（4 位小数，无数据为空） */
    private BigDecimal monthlyAvgPassRate;
}
