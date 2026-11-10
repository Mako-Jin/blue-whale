package com.bluewhale.crm.analytics.domain.repository;

import com.bluewhale.crm.analytics.domain.model.AnalyticsExport;

/**
 * 分析导出任务仓储。
 *
 * @author BlueWhale
 */
public interface AnalyticsExportRepository {

    /**
     * 新增导出任务。
     *
     * @param export 导出任务
     */
    void insert(AnalyticsExport export);

    /**
     * 更新导出任务。
     *
     * @param export 导出任务
     */
    void update(AnalyticsExport export);

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 导出任务，未找到返回 null
     */
    AnalyticsExport findById(Long id);

    /**
     * 按单号前缀统计数量，用于生成流水号。
     *
     * @param prefix 前缀
     * @return 数量
     */
    long countByPrefix(String prefix);
}
