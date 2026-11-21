package com.bluewhale.workflow.po.stat;

import lombok.Data;

/**
 * 流程实例的三个即时数（政事殿概览 T5.6）。
 *
 * <p>三个数出自同一条 SQL：分三条查会让"运行中 12、今日发起 30、今日办结 40"这种
 * 自相矛盾的组合出现在同一屏上——三条查询之间刚好处在两次审批之间时就会对不上。</p>
 *
 * @author BlueWhale
 */
@Data
public class ProcessInstanceStatPo {

    /** 运行中的实例数（{@code status = 0}） */
    private Long running;

    /** 今日发起的实例数（{@code create_time} 落在当日窗口内，不限状态） */
    private Long startedToday;

    /** 今日办结的实例数（{@code status = 1} 且 {@code finished_at} 落在当日窗口内） */
    private Long finishedToday;
}
