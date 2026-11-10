package com.bluewhale.crm.sales.domain.repository;

import com.bluewhale.crm.sales.domain.model.ForecastPeriodType;
import com.bluewhale.crm.sales.domain.model.ForecastQuery;
import com.bluewhale.crm.sales.domain.model.ForecastScopeType;
import com.bluewhale.crm.sales.domain.model.ForecastSnapshot;

import java.util.List;

/**
 * 销售预测快照仓储端口。
 *
 * @author BlueWhale
 */
public interface ForecastSnapshotRepository {

    /**
     * 分页查询预测快照。
     *
     * @param query 查询条件
     * @return 当前页快照
     */
    List<ForecastSnapshot> page(ForecastQuery query);

    /**
     * 统计符合条件的预测快照数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(ForecastQuery query);

    /**
     * 按主键查询。
     *
     * @param id 快照 ID
     * @return 快照；不存在返回 {@code null}
     */
    ForecastSnapshot findById(Long id);

    /**
     * 按周期与范围查询唯一快照。
     *
     * @param period 周期
     * @param periodType 周期类型
     * @param scopeType 范围类型
     * @param ownerId 负责人（USER 范围为负责人 ID，DEPT 范围传 0）
     * @param deptId 部门（DEPT 范围为部门 ID，USER 范围传 0）
     * @return 快照；不存在返回 {@code null}
     */
    ForecastSnapshot findByPeriodScope(String period, ForecastPeriodType periodType,
                                       ForecastScopeType scopeType, Long ownerId, Long deptId);

    /**
     * 新增快照。
     *
     * @param snapshot 快照
     */
    void insert(ForecastSnapshot snapshot);

    /**
     * 更新快照（乐观锁，版本冲突返回 false）。
     *
     * @param snapshot 快照
     * @return 更新成功返回 true
     */
    boolean update(ForecastSnapshot snapshot);
}
