package com.bluewhale.lms.analytics.domain.repository;

import com.bluewhale.lms.analytics.domain.model.MetricQuery;
import com.bluewhale.lms.analytics.domain.model.MetricSnapshot;
import com.bluewhale.lms.analytics.domain.model.MetricScope;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 指标快照仓储。
 *
 * @author BlueWhale
 */
public interface MetricSnapshotRepository {

    /**
     * 查询某统计日某范围的快照。
     *
     * @param statDate 统计日
     * @param scopeType 统计范围
     * @param scopeId 范围对象 ID
     * @return 快照
     */
    Optional<MetricSnapshot> find(LocalDate statDate, MetricScope scopeType, Long scopeId);

    /**
     * 保存快照：同统计日同范围存在则覆盖，不存在则插入。
     *
     * @param snapshot 快照
     * @return 主键
     */
    Long save(MetricSnapshot snapshot);

    /**
     * 分页查询历史快照。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 条数
     * @return 快照列表
     */
    List<MetricSnapshot> page(MetricQuery query, long offset, int size);

    /**
     * 统计历史快照总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(MetricQuery query);

    /**
     * 查询最近若干个统计日的全院快照（趋势用，按统计日升序）。
     *
     * @param days 天数
     * @return 快照列表
     */
    List<MetricSnapshot> listRecentAll(int days);
}
