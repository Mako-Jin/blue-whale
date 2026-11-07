package com.bluewhale.mall.catalog.domain.repository;

import com.bluewhale.mall.catalog.domain.model.SkuStock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * SKU 库存仓储。
 *
 * <p>「预占 / 释放 / 出库」三个动作都是<b>条件更新</b>，返回布尔表示是否成功：
 * 返回 false 说明并发下数量已不满足，调用方必须当作「库存不足」处理，
 * 不能忽略返回值继续建单。</p>
 *
 * @author BlueWhale
 */
public interface SkuStockRepository {

    /**
     * 按 SKU ID 查询库存。
     *
     * @param skuId SKU ID
     * @return 库存
     */
    Optional<SkuStock> findBySkuId(Long skuId);

    /**
     * 按 SKU ID 批量查询。
     *
     * @param skuIds SKU ID 集合
     * @return 库存列表
     */
    List<SkuStock> findBySkuIds(Collection<Long> skuIds);

    /**
     * 分页查询库存（按 SKU ID 过滤）。
     *
     * @param skuIds SKU ID 过滤（空集合表示不过滤）
     * @param offset 偏移量
     * @param size 条数
     * @return 库存列表
     */
    List<SkuStock> page(Collection<Long> skuIds, long offset, int size);

    /**
     * 统计库存行数。
     *
     * @param skuIds SKU ID 过滤（空集合表示不过滤）
     * @return 总数
     */
    long count(Collection<Long> skuIds);

    /**
     * 初始化库存行（新建 SKU 时调用，已存在则忽略）。
     *
     * @param skuId SKU ID
     */
    void init(Long skuId);

    /**
     * 预占库存：可售减、预占增（条件更新，可售不足时不生效）。
     *
     * @param skuId SKU ID
     * @param quantity 数量
     * @return 成功返回 true；库存不足返回 false
     */
    boolean reserve(Long skuId, int quantity);

    /**
     * 释放预占：可售增、预占减（取消订单时调用）。
     *
     * @param skuId SKU ID
     * @param quantity 数量
     * @return 成功返回 true
     */
    boolean release(Long skuId, int quantity);

    /**
     * 出库：预占减（发货时调用，可售在下单时已扣，这里不能再扣一次）。
     *
     * @param skuId SKU ID
     * @param quantity 数量
     * @return 成功返回 true
     */
    boolean ship(Long skuId, int quantity);

    /**
     * 运营调整可售数量（增量，可为负）。
     *
     * @param skuId SKU ID
     * @param delta 增量
     * @return 调整后的可售数量
     */
    int adjust(Long skuId, int delta);
}
