package com.bluewhale.crm.sales.domain.repository;

import com.bluewhale.crm.sales.domain.model.ForecastPeriodType;
import com.bluewhale.crm.sales.domain.model.ForecastScopeType;
import com.bluewhale.crm.sales.domain.model.SalesTarget;
import com.bluewhale.crm.sales.domain.model.SalesTargetQuery;

import java.util.List;

/**
 * 销售目标仓储端口。
 *
 * @author BlueWhale
 */
public interface SalesTargetRepository {

    /**
     * 分页查询销售目标。
     *
     * @param query 查询条件
     * @return 当前页目标
     */
    List<SalesTarget> page(SalesTargetQuery query);

    /**
     * 统计符合条件的销售目标数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(SalesTargetQuery query);

    /**
     * 按主键查询。
     *
     * @param id 目标 ID
     * @return 目标；不存在返回 {@code null}
     */
    SalesTarget findById(Long id);

    /**
     * 按周期与范围查询唯一目标。
     *
     * @param period 周期
     * @param periodType 周期类型
     * @param scopeType 范围类型
     * @param ownerId 负责人（USER 范围为负责人 ID，DEPT 范围传 0）
     * @param deptId 部门（DEPT 范围为部门 ID，USER 范围传 0）
     * @return 目标；不存在返回 {@code null}
     */
    SalesTarget findByPeriodScope(String period, ForecastPeriodType periodType,
                                  ForecastScopeType scopeType, Long ownerId, Long deptId);

    /**
     * 新增销售目标。
     *
     * @param target 目标
     */
    void insert(SalesTarget target);

    /**
     * 更新销售目标（乐观锁，版本冲突返回 false）。
     *
     * @param target 目标
     * @return 更新成功返回 true
     */
    boolean update(SalesTarget target);

    /**
     * 逻辑删除销售目标。
     *
     * @param id 目标 ID
     * @return 删除成功返回 true
     */
    boolean deleteById(Long id);
}
