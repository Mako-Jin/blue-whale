package com.bluewhale.crm.commerce.domain.repository;

import com.bluewhale.crm.commerce.domain.model.ArSnapshot;
import com.bluewhale.crm.commerce.domain.model.ArSnapshotQuery;

import java.time.LocalDate;
import java.util.List;

/**
 * 客户应收快照仓储端口。
 *
 * @author BlueWhale
 */
public interface ArSnapshotRepository {

    /**
     * 分页查询快照。
     *
     * @param query 查询条件
     * @return 快照列表
     */
    List<ArSnapshot> page(ArSnapshotQuery query);

    /**
     * 统计快照总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(ArSnapshotQuery query);

    /**
     * 按客户与日期查询快照。
     *
     * @param customerId 客户 ID
     * @param snapshotDate 快照日期
     * @param currency 币种
     * @return 快照；不存在返回 {@code null}
     */
    ArSnapshot findByCustomerAndDate(Long customerId, LocalDate snapshotDate, String currency);

    /**
     * 按客户与日期幂等保存（存在则更新，不存在则插入）。
     *
     * @param snapshot 快照
     */
    void upsert(ArSnapshot snapshot);
}
