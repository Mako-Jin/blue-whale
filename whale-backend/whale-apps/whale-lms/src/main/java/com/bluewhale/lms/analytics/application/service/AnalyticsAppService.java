package com.bluewhale.lms.analytics.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.analytics.application.view.AnalyticsOverviewView;
import com.bluewhale.lms.analytics.application.view.MetricSnapshotView;
import com.bluewhale.lms.analytics.application.view.RefreshResultView;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.analytics.domain.model.MetricQuery;

import java.time.LocalDate;

/**
 * 学习看板应用服务。
 *
 * @author BlueWhale
 */
public interface AnalyticsAppService {

    /**
     * 看板总览。
     *
     * <p>统计日已有快照则读快照，没有则实时算一份但<b>不落库</b>
     * （落库是 {@link #refresh} 的职责，看板只读）。</p>
     *
     * @param statDate 统计日（为空取今天）
     * @param actor 操作人
     * @return 总览
     */
    AnalyticsOverviewView overview(LocalDate statDate, LmsActor actor);

    /**
     * 刷新并落库某统计日的全院快照（同日重跑覆盖）。
     *
     * @param statDate 统计日（为空取今天）
     * @param actor 操作人
     * @return 刷新结果
     */
    RefreshResultView refresh(LocalDate statDate, LmsActor actor);

    /**
     * 历史快照分页。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<MetricSnapshotView> page(MetricQuery query, LmsActor actor);

    /**
     * 导出近 N 天快照为 CSV（聚合口径，不含个人信息）。
     *
     * @param days 天数（1–366）
     * @param actor 操作人
     * @return CSV 文本
     */
    String exportCsv(int days, LmsActor actor);
}
