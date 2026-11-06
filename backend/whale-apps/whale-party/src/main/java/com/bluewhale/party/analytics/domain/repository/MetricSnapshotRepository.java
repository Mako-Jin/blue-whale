package com.bluewhale.party.analytics.domain.repository;

import com.bluewhale.party.analytics.domain.model.MetricScope;
import com.bluewhale.party.analytics.domain.model.MetricSnapshot;

import java.time.LocalDate;
import java.util.List;

/**
 * 统计快照仓储端口。
 *
 * @author BlueWhale
 */
public interface MetricSnapshotRepository {

    /**
     * 查询指定统计日的全部快照。
     *
     * @param statDate 统计日
     * @return 快照列表
     */
    List<MetricSnapshot> listByDate(LocalDate statDate);

    /**
     * 查询指定统计日与范围的快照。
     *
     * @param statDate 统计日
     * @param scope 范围
     * @param scopeId 范围 ID（ALL 传 0）
     * @return 快照列表
     */
    List<MetricSnapshot> listByDate(LocalDate statDate, MetricScope scope, Long scopeId);

    /**
     * 查询一段统计日内的单指标曲线。
     *
     * @param from 起始日（含）
     * @param to 结束日（含）
     * @param metricType 指标编码
     * @param scope 范围
     * @param scopeId 范围 ID
     * @return 快照列表（按统计日升序）
     */
    List<MetricSnapshot> listTrend(LocalDate from, LocalDate to, String metricType,
                                   MetricScope scope, Long scopeId);

    /**
     * 查询一段统计日内某范围的全部指标（导出用）。
     *
     * @param from 起始日（含）
     * @param to 结束日（含）
     * @param scope 范围
     * @param scopeId 范围 ID
     * @return 快照列表（按统计日、指标升序）
     */
    List<MetricSnapshot> listBetween(LocalDate from, LocalDate to, MetricScope scope, Long scopeId);

    /**
     * 覆盖写快照（按 统计日+指标+范围 唯一键 upsert）。
     *
     * @param snapshots 快照列表
     */
    void saveAll(List<MetricSnapshot> snapshots);
}
