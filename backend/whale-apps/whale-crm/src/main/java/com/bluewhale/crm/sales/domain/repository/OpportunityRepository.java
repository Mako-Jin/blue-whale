package com.bluewhale.crm.sales.domain.repository;

import com.bluewhale.crm.sales.domain.model.Opportunity;
import com.bluewhale.crm.sales.domain.model.OpportunityQuery;

import java.util.List;

/**
 * 商机仓储端口。
 *
 * @author BlueWhale
 */
public interface OpportunityRepository {

    /**
     * 分页查询商机。
     *
     * @param query 查询条件
     * @return 当前页商机
     */
    List<Opportunity> page(OpportunityQuery query);

    /**
     * 统计符合条件的商机数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(OpportunityQuery query);

    /**
     * 查询不分页的商机列表（漏斗聚合使用）。
     *
     * @param query 查询条件
     * @return 商机列表
     */
    List<Opportunity> list(OpportunityQuery query);

    /**
     * 按主键查询。
     *
     * @param id 商机 ID
     * @return 商机；不存在返回 {@code null}
     */
    Opportunity findById(Long id);

    /**
     * 新增商机。
     *
     * @param opportunity 商机
     */
    void insert(Opportunity opportunity);

    /**
     * 更新商机（乐观锁，版本冲突返回 false）。
     *
     * @param opportunity 商机
     * @return 更新成功返回 true
     */
    boolean update(Opportunity opportunity);
}
