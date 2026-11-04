package com.bluewhale.erp.analytics.domain.repository;

import com.bluewhale.erp.analytics.domain.model.CockpitSnapshot;
import com.bluewhale.erp.analytics.domain.model.CostFact;
import com.bluewhale.erp.analytics.domain.model.QualityFact;
import com.bluewhale.erp.analytics.domain.model.ReceivableFact;
import com.bluewhale.erp.analytics.domain.model.SalesFact;
import com.bluewhale.erp.analytics.domain.model.StockFact;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 驾驶舱快照与只读事实仓储。
 *
 * @author BlueWhale
 */
public interface AnalyticsRepository {

    /**
     * 查询指定期间当前有效快照。
     *
     * @param periodCode 统计期间
     * @return 快照
     */
    Optional<CockpitSnapshot> findActiveSnapshot(String periodCode);

    /**
     * 按期间与统计日查询快照。
     *
     * @param periodCode 统计期间
     * @param statDate 统计截止日期
     * @return 快照
     */
    Optional<CockpitSnapshot> findSnapshot(String periodCode, LocalDate statDate);

    /**
     * 生成快照编号。
     *
     * @param periodCode 统计期间
     * @return 快照编号
     */
    String nextSnapshotNo(String periodCode);

    /**
     * 新增快照。
     *
     * @param snapshot 快照
     */
    void insertSnapshot(CockpitSnapshot snapshot);

    /**
     * 更新快照。
     *
     * @param snapshot 快照
     */
    void updateSnapshot(CockpitSnapshot snapshot);

    /**
     * 将期间内有效快照置为失效。
     *
     * @param periodCode 统计期间
     */
    void markPeriodStale(String periodCode);

    /**
     * 查询销售事实。
     *
     * @param from 单据日期起（可空）
     * @param to 单据日期止（可空）
     * @return 销售事实
     */
    List<SalesFact> listSalesFacts(LocalDate from, LocalDate to);

    /**
     * 查询出库成本事实。
     *
     * @param from 出库日期起（可空）
     * @param to 出库日期止（可空）
     * @return 成本事实
     */
    List<CostFact> listCostFacts(LocalDate from, LocalDate to);

    /**
     * 查询库存事实。
     *
     * @return 库存事实
     */
    List<StockFact> listStockFacts();

    /**
     * 查询应收事实。
     *
     * @return 应收事实
     */
    List<ReceivableFact> listReceivableFacts();

    /**
     * 查询质量异常事实。
     *
     * @return 质量事实
     */
    List<QualityFact> listQualityFacts();
}
