package com.bluewhale.erp.scm.domain.repository;

import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.model.StockBalance;
import com.bluewhale.erp.scm.domain.model.StockMovement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 库存仓储：余额按「物料 + 仓库」唯一，流水只追加。
 *
 * @author BlueWhale
 */
public interface StockRepository {

    /**
     * 分页查询库存余额。
     *
     * @param query 查询条件
     * @return 余额列表
     */
    List<StockBalance> pageBalances(ScmQuery query);

    /**
     * 统计库存余额数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long countBalances(ScmQuery query);

    /**
     * 不分页查询库存余额（呆滞分析用）。
     *
     * @param query 查询条件
     * @return 余额列表
     */
    List<StockBalance> listBalances(ScmQuery query);

    /**
     * 按物料编码查询全部仓库余额（内网只读契约）
     *
     * @param itemCode 物料编码
     * @return 余额列表
     */
    List<StockBalance> listBalancesByItemCode(String itemCode);

    /**
     * 查询物料在指定仓库的最近出库时间。
     *
     * @param itemId 物料主键
     * @param warehouseId 仓库主键
     * @return 最近出库时间；从未出库返回空
     */
    Optional<LocalDateTime> lastOutboundAt(Long itemId, Long warehouseId);

    /**
     * 分页查询库存流水。
     *
     * @param query 查询条件
     * @return 流水列表
     */
    List<StockMovement> pageMovements(ScmQuery query);

    /**
     * 统计库存流水数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long countMovements(ScmQuery query);

    /**
     * 查询指定物料在指定仓库的余额。
     *
     * @param itemId 物料主键
     * @param warehouseId 仓库主键
     * @return 余额
     */
    Optional<StockBalance> findBalance(Long itemId, Long warehouseId);

    /**
     * 新增库存流水。
     *
     * @param movement 流水
     */
    void insertMovement(StockMovement movement);

    /**
     * 新增或更新库存余额。
     *
     * @param balance 余额
     */
    void saveBalance(StockBalance balance);
}
