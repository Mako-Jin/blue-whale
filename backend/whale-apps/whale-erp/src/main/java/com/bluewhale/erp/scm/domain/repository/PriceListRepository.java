package com.bluewhale.erp.scm.domain.repository;

import com.bluewhale.erp.scm.domain.model.PriceList;
import com.bluewhale.erp.scm.domain.model.PriceListQuery;
import com.bluewhale.erp.scm.domain.model.PriceType;

import java.util.List;
import java.util.Optional;

/**
 * 价目表仓储。
 *
 * @author BlueWhale
 */
public interface PriceListRepository {

    /**
     * 分页查询价目表。
     *
     * @param query 查询条件
     * @return 价目列表
     */
    List<PriceList> page(PriceListQuery query);

    /**
     * 统计价目表数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(PriceListQuery query);

    /**
     * 按主键查询价目。
     *
     * @param id 主键
     * @return 价目
     */
    Optional<PriceList> findById(Long id);

    /**
     * 不分页查询价目（比价用）。
     *
     * @param query 查询条件
     * @return 价目列表
     */
    List<PriceList> list(PriceListQuery query);

    /**
     * 按「价格类型 + 往来方 + 物料」查询唯一价目。
     *
     * @param priceType 价格类型
     * @param partnerId 往来方主键
     * @param itemId 物料主键
     * @return 价目
     */
    Optional<PriceList> find(PriceType priceType, Long partnerId, Long itemId);

    /**
     * 新增价目。
     *
     * @param priceList 价目
     */
    void insert(PriceList priceList);

    /**
     * 更新价目。
     *
     * @param priceList 价目
     */
    void update(PriceList priceList);
}
