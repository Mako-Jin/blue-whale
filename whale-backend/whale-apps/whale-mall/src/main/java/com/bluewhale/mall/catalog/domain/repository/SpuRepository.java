package com.bluewhale.mall.catalog.domain.repository;

import com.bluewhale.mall.catalog.domain.model.Sku;
import com.bluewhale.mall.catalog.domain.model.Spu;
import com.bluewhale.mall.catalog.domain.model.SpuQuery;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 商品仓储（SPU 聚合：商品 + 规格 + 价目）。
 *
 * @author BlueWhale
 */
public interface SpuRepository {

    /**
     * 分页查询商品（不带 SKU：列表页只需要封面与状态）。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 条数
     * @return 商品列表
     */
    List<Spu> page(SpuQuery query, long offset, int size);

    /**
     * 统计商品数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(SpuQuery query);

    /**
     * 按 ID 查询聚合（含 SKU 与价目）。
     *
     * @param id 商品 ID
     * @return 商品聚合
     */
    Optional<Spu> findById(Long id);

    /**
     * 按 ID 批量查询聚合（含 SKU 与价目）。
     *
     * @param ids 商品 ID 集合
     * @return 商品聚合列表
     */
    List<Spu> findByIds(Collection<Long> ids);

    /**
     * 按 SKU ID 查询所属商品聚合（含 SKU 与价目）。
     *
     * @param skuIds SKU ID 集合
     * @return 商品聚合列表（与命中的 SKU 对应的商品去重）
     */
    List<Spu> findBySkuIds(Collection<Long> skuIds);

    /**
     * 按 SKU 编码模糊搜索 SKU ID（库存页按编码筛选用）。
     *
     * @param keyword 编码关键字
     * @param limit 返回上限
     * @return SKU ID 列表
     */
    List<Long> findSkuIdsByCode(String keyword, int limit);

    /**
     * 按 ID 批量查询 SKU（轻量，不含价目）。
     *
     * @param skuIds SKU ID 集合
     * @return SKU 列表
     */
    List<Sku> findSkusByIds(Collection<Long> skuIds);

    /**
     * 商品编码是否已存在（排除指定 ID）。
     *
     * @param spuCode 商品编码
     * @param excludeId 需排除的 ID，可为 null
     * @return 存在返回 true
     */
    boolean existsCode(String spuCode, Long excludeId);

    /**
     * SKU 编码是否已被占用（排除指定商品内的 SKU）。
     *
     * @param skuCodes SKU 编码集合
     * @param excludeSpuId 需排除的商品 ID，可为 null
     * @return 冲突的编码列表
     */
    List<String> findConflictingSkuCodes(Collection<String> skuCodes, Long excludeSpuId);

    /**
     * 保存聚合（新建或更新；SKU 与价目全量覆盖）。
     *
     * @param spu 商品聚合
     * @return 商品 ID
     */
    Long save(Spu spu);

    /**
     * 更新商品状态。
     *
     * @param id 商品 ID
     * @param status 目标状态
     */
    void updateStatus(Long id, String status);

    /**
     * 逻辑删除商品及其 SKU、价目（编码改写为 {@code 原编码#D{id}} 以释放唯一键）。
     *
     * @param id 商品 ID
     */
    void remove(Long id);

    /**
     * 统计指定类目下的商品数（含子类目由调用方合并后传入）。
     *
     * @param categoryIds 类目 ID 集合
     * @return 商品数
     */
    long countByCategoryIds(Collection<Long> categoryIds);

    /**
     * 统计全部商品数（用于类目删除前校验）。
     *
     * @return 商品数
     */
    long countAll();
}
