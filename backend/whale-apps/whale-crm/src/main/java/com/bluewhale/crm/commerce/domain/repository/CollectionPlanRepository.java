package com.bluewhale.crm.commerce.domain.repository;

import com.bluewhale.crm.commerce.domain.model.CollectionPlan;
import com.bluewhale.crm.commerce.domain.model.CollectionQuery;

import java.util.List;

/**
 * 回款计划仓储端口。
 *
 * @author BlueWhale
 */
public interface CollectionPlanRepository {

    /**
     * 分页查询回款计划。
     *
     * @param query 查询条件
     * @return 计划列表
     */
    List<CollectionPlan> page(CollectionQuery query);

    /**
     * 统计回款计划总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(CollectionQuery query);

    /**
     * 按 ID 查询回款计划。
     *
     * @param id 计划 ID
     * @return 计划；不存在返回 {@code null}
     */
    CollectionPlan findById(Long id);

    /**
     * 查询指定合同的回款计划。
     *
     * @param contractId 合同台账 ID
     * @return 计划列表
     */
    List<CollectionPlan> findByContract(Long contractId);

    /**
     * 全量替换指定合同的回款分期。
     *
     * @param contractId 合同台账 ID
     * @param customerId 客户 ID
     * @param ownerId 负责人
     * @param deptId 归属部门
     * @param plans 分期列表
     * @return 保存后的分期列表
     */
    List<CollectionPlan> replaceByContract(Long contractId, Long customerId, Long ownerId, Long deptId,
                                          List<CollectionPlan> plans);

    /**
     * 更新承诺还款字段（乐观锁）。
     *
     * @param plan 计划
     * @return 更新成功返回 true
     */
    boolean updatePromise(CollectionPlan plan);

    /**
     * 更新状态（乐观锁）。
     *
     * @param plan 计划
     * @return 更新成功返回 true
     */
    boolean update(CollectionPlan plan);
}
