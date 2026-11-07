package com.bluewhale.crm.analytics.application.service;

import com.bluewhale.crm.analytics.domain.model.AnalyticsExport;
import com.bluewhale.crm.analytics.domain.model.ExportType;
import com.bluewhale.crm.common.CrmActor;

/**
 * 分析导出应用服务。
 *
 * @author BlueWhale
 */
public interface AnalyticsExportAppService {

    /**
     * 创建并同步执行分析导出。
     *
     * @param type 导出类型
     * @param filtersJson 筛选条件 JSON
     * @param actor 操作人
     * @return 导出任务
     */
    AnalyticsExport create(ExportType type, String filtersJson, CrmActor actor);

    /**
     * 查询导出内容。
     *
     * @param id 导出任务主键
     * @param actor 操作人
     * @return 导出任务（含 CSV 内容）
     */
    AnalyticsExport content(Long id, CrmActor actor);
}
