package com.bluewhale.mes.dispatch.vo;

import lombok.Data;

/**
 * 有限产能排程生成结果。
 *
 * <p>{@code unscheduledTasks} 表示排程窗口内产能不足、未完全分配的任务数，剩余数量仍保留在任务上。</p>
 *
 * @author BlueWhale
 */
@Data
public class MesScheduleGenerateVO {

    /** 生成的排程计划数 */
    private Integer planCount;

    /** 生成的时段数 */
    private Integer slotCount;

    /** 窗口内容量不足未完全排入的任务数 */
    private Integer unscheduledTasks;

    /** 提示信息 */
    private String message;
}
