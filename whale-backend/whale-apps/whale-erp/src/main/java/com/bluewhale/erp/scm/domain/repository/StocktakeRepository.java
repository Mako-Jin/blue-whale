package com.bluewhale.erp.scm.domain.repository;

import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.model.Stocktake;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 库存盘点单仓储。
 *
 * @author BlueWhale
 */
public interface StocktakeRepository {

    /**
     * 分页查询盘点单。
     *
     * @param query 查询条件
     * @return 盘点单列表
     */
    List<Stocktake> page(ScmQuery query);

    /**
     * 统计盘点单数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(ScmQuery query);

    /**
     * 按主键查询盘点单（含分录）。
     *
     * @param id 主键
     * @return 盘点单
     */
    Optional<Stocktake> findById(Long id);

    /**
     * 生成盘点单号。
     *
     * @param stocktakeDate 盘点日期
     * @return 单号
     */
    String nextDocNo(LocalDate stocktakeDate);

    /**
     * 新增盘点单。
     *
     * @param stocktake 盘点单
     */
    void insert(Stocktake stocktake);

    /**
     * 更新盘点单头与分录。
     *
     * @param stocktake 盘点单
     */
    void update(Stocktake stocktake);
}
