package com.bluewhale.crm.marketing.domain.repository;

import com.bluewhale.crm.marketing.domain.model.MarketingSend;
import com.bluewhale.crm.marketing.domain.model.MarketingSendQuery;

import java.util.List;

/**
 * 批量触达任务仓储。
 *
 * @author BlueWhale
 */
public interface MarketingSendRepository {

    /**
     * 分页查询触达任务。
     *
     * @param query 查询条件
     * @return 任务列表
     */
    List<MarketingSend> page(MarketingSendQuery query);

    /**
     * 统计触达任务数量。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(MarketingSendQuery query);

    /**
     * 按主键查询触达任务。
     *
     * @param id 主键
     * @return 任务；不存在返回 null
     */
    MarketingSend findById(Long id);

    /**
     * 新增触达任务。
     *
     * @param send 任务
     */
    void insert(MarketingSend send);

    /**
     * 更新触达任务。
     *
     * @param send 任务
     */
    void update(MarketingSend send);

    /**
     * 统计指定前缀的任务号数量。
     *
     * @param prefix 前缀
     * @return 数量
     */
    long countBySendNoPrefix(String prefix);
}
