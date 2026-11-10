package com.bluewhale.mes.dispatch.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 生产看板视图对象
 *
 * <p>今日目标 / 完成数 / 不良数 / 异常数与达成率按报工与任务汇总，支持车间、产线、班组筛选。</p>
 *
 * @author BlueWhale
 */
@Data
public class MesBoardVO {

    /** 统计日期 */
    private LocalDate boardDate;

    /** 今日目标数量（计划开工数量） */
    private BigDecimal targetQuantity;

    /** 今日完成数量（合格数） */
    private BigDecimal completedQuantity;

    /** 今日不良数量 */
    private BigDecimal defectQuantity;

    /** 达成率（百分比，保留两位小数） */
    private BigDecimal achievementRate;

    /** 未关闭异常数 */
    private Long exceptionCount;

    /** 执行中任务数 */
    private Long activeTaskCount;

    /** 执行中任务明细 */
    private List<MesWorkOrderTaskVO> activeTasks;
}
