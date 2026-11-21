package com.bluewhale.workflow.po.stat;

import lombok.Data;

/**
 * 超时待办的规模与最坏情况（政事殿概览 T5.6）。
 *
 * <p>只给个数不够：1 个待办超时 3 小时和 1 个超时 3 天，处置动作完全不同
 * ——前者是"催一下"，后者是"这条审批链可能没人认领"。所以同时把最久超时
 * 多少小时捞回来，告警文案里直接说。</p>
 *
 * @author BlueWhale
 */
@Data
public class ProcessOverdueStatPo {

    /** 已超时的待办数（{@code status = 0} 且 {@code due_at <= now}） */
    private Long overdue;

    /** 最久超时多少分钟；无超时时为 0 */
    private Long worstMinutes;
}
