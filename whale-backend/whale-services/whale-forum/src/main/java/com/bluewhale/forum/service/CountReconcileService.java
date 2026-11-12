package com.bluewhale.forum.service;

import com.bluewhale.forum.dto.ReconcileReportVO;

/**
 * 计数对账：以明细表 {@code COUNT} 校正展示计数。
 *
 * <p>展示计数走增量维护（{@code SET x = x ± 1}），快、但会漂：漏一次回滚、多一次补偿、
 * 人工改数，都对不上明细。对账的作用不是「修复 bug」，而是把漂移限制在一天之内，
 * 并留下可追溯的轨迹——没有轨迹的自动修数，出事时没人能证明修的是什么。</p>
 *
 * <p>只对账可重算的三项：回复、投花、收藏。浏览数没有逐次明细（去重表是窗口判定行，
 * 不是浏览流水），硬对账会把「同一人 24h 内看 5 次」压成 1，越对越错。</p>
 *
 * @author BlueWhale
 */
public interface CountReconcileService {

    /**
     * 核对当前租户的计数。
     *
     * @param fix 是否按真实值修正（{@code false} 只报告）
     * @param limit 单批帖子数，{@code null} 取配置默认
     * @param operatorId 触发人（定时任务传 {@code null}）
     * @return 对账结果
     */
    ReconcileReportVO reconcile(boolean fix, Integer limit, Long operatorId);
}
