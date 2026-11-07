package com.bluewhale.crm.analytics.domain.repository;

import com.bluewhale.crm.analytics.domain.model.MetricSnapshot;
import com.bluewhale.crm.analytics.domain.model.MetricSnapshotQuery;

import java.util.List;

/**
 * 指标快照仓储。
 *
 * @author BlueWhale
 */
public interface MetricSnapshotRepository {

    /**
     * 按唯一键幂等写入：存在则更新，不存在则插入；禁止删除。
     *
     * @param snapshot 指标快照
     */
    void upsert(MetricSnapshot snapshot);

    /**
     * 按唯一键查询。
     *
     * @param tenantId 租户 ID
     * @param snapshot 包含指标、统计日、维度信息的快照
     * @return 已存在的快照，未找到返回 null
     */
    MetricSnapshot findByUniqueKey(Long tenantId, MetricSnapshot snapshot);

    /**
     * 分页查询指标快照。
     *
     * @param query 查询条件
     * @return 快照列表
     */
    List<MetricSnapshot> page(MetricSnapshotQuery query);

    /**
     * 查询指标快照总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(MetricSnapshotQuery query);
}
