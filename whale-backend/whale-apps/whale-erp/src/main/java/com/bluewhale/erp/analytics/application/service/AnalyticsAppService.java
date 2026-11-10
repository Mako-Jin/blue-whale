package com.bluewhale.erp.analytics.application.service;

import com.bluewhale.erp.analytics.application.command.CockpitRefreshCommand;
import com.bluewhale.erp.analytics.application.view.CockpitView;
import com.bluewhale.erp.analytics.application.view.CubeResultView;
import com.bluewhale.erp.analytics.domain.model.AnalyticsQuery;

/**
 * 驾驶舱与多维分析应用服务。
 *
 * @author BlueWhale
 */
public interface AnalyticsAppService {

    /**
     * 查询驾驶舱（缓存缺失时按当前口径懒加载）。
     *
     * @param periodCode 统计期间 yyyyMM，可空
     * @return 驾驶舱视图
     */
    CockpitView getCockpit(String periodCode);

    /**
     * 刷新驾驶舱快照：旧快照置 STALE，再写入新快照。
     *
     * @param command 刷新命令
     * @return 驾驶舱视图
     */
    CockpitView refreshCockpit(CockpitRefreshCommand command);

    /**
     * 主动失效指定期间快照。
     *
     * @param periodCode 统计期间
     */
    void invalidateCockpit(String periodCode);

    /**
     * 多维分析聚合。
     *
     * @param query 查询条件（维度、指标、日期区间）
     * @return 聚合结果
     */
    CubeResultView cube(AnalyticsQuery query);
}
