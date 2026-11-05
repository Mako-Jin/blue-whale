package com.bluewhale.erp.scm.domain.repository;

import com.bluewhale.erp.scm.domain.model.SafetyStock;
import com.bluewhale.erp.scm.domain.model.SafetyStockQuery;

import java.util.List;
import java.util.Optional;

/**
 * 安全库存仓储。
 *
 * @author BlueWhale
 */
public interface SafetyStockRepository {

    /**
     * 分页查询安全库存。
     *
     * @param query 查询条件
     * @return 安全库存列表
     */
    List<SafetyStock> page(SafetyStockQuery query);

    /**
     * 统计安全库存数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(SafetyStockQuery query);

    /**
     * 不分页查询安全库存（预警计算用）。
     *
     * @param query 查询条件
     * @return 安全库存列表
     */
    List<SafetyStock> list(SafetyStockQuery query);

    /**
     * 按主键查询安全库存。
     *
     * @param id 主键
     * @return 安全库存
     */
    Optional<SafetyStock> findById(Long id);

    /**
     * 按「仓库 + 物料」查询安全库存。
     *
     * @param warehouseId 仓库主键
     * @param itemId 物料主键
     * @return 安全库存
     */
    Optional<SafetyStock> find(Long warehouseId, Long itemId);

    /**
     * 新增安全库存。
     *
     * @param safetyStock 安全库存
     */
    void insert(SafetyStock safetyStock);

    /**
     * 更新安全库存。
     *
     * @param safetyStock 安全库存
     */
    void update(SafetyStock safetyStock);

    /**
     * 逻辑删除安全库存。
     *
     * @param id 主键
     */
    void deleteById(Long id);
}
