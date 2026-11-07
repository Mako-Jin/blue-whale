package com.bluewhale.crm.analytics.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.analytics.domain.model.CollectionAnalysisData;
import com.bluewhale.crm.analytics.domain.model.CustomerAnalysisData;
import com.bluewhale.crm.analytics.domain.model.DashboardData;
import com.bluewhale.crm.analytics.domain.model.FunnelViewData;
import com.bluewhale.crm.analytics.domain.model.MetricSnapshot;
import com.bluewhale.crm.analytics.domain.model.MetricSnapshotQuery;
import com.bluewhale.crm.analytics.domain.model.RankingRow;
import com.bluewhale.crm.common.CrmActor;

import java.time.LocalDate;
import java.util.List;

/**
 * 数据分析应用服务。
 *
 * @author BlueWhale
 */
public interface AnalyticsAppService {

    /**
     * 销售看板。
     *
     * @param actor 操作人
     * @return 看板数据
     */
    DashboardData dashboard(CrmActor actor);

    /**
     * 漏斗分析。
     *
     * @param startDate 起始日，可空（默认近 30 天）
     * @param endDate 结束日，可空（默认今天）
     * @param actor 操作人
     * @return 漏斗数据
     */
    FunnelViewData funnel(LocalDate startDate, LocalDate endDate, CrmActor actor);

    /**
     * 客户分析。
     *
     * @param statDate 统计日，可空（默认今天）
     * @param actor 操作人
     * @return 客户分析数据
     */
    CustomerAnalysisData customerAnalysis(LocalDate statDate, CrmActor actor);

    /**
     * 销售排行。
     *
     * @param startDate 起始日，可空（默认近 30 天）
     * @param endDate 结束日，可空（默认今天）
     * @param actor 操作人
     * @return 排行行
     */
    List<RankingRow> ranking(LocalDate startDate, LocalDate endDate, CrmActor actor);

    /**
     * 回款分析。
     *
     * @param statDate 统计日，可空（默认今天）
     * @param actor 操作人
     * @return 回款分析数据
     */
    CollectionAnalysisData collectionAnalysis(LocalDate statDate, CrmActor actor);

    /**
     * 生成指定日期的指标快照（重跑覆盖，不新增）。
     *
     * @param statDate 统计日，可空（默认昨日）
     * @return 生成条数
     */
    int generateSnapshots(LocalDate statDate);

    /**
     * 分页查询指标快照。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<MetricSnapshot> listSnapshots(MetricSnapshotQuery query, CrmActor actor);
}
