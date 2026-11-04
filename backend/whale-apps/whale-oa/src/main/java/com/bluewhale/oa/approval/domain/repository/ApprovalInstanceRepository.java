package com.bluewhale.oa.approval.domain.repository;

import com.bluewhale.oa.approval.domain.model.ApprovalInstance;
import com.bluewhale.oa.approval.domain.model.ApprovalInstanceQuery;

import java.util.List;
import java.util.Optional;

/**
 * 审批实例仓储。
 *
 * @author BlueWhale
 */
public interface ApprovalInstanceRepository {

    /**
     * 分页查询审批实例。
     *
     * @param query 查询条件
     * @return 当前页审批实例
     */
    List<ApprovalInstance> page(ApprovalInstanceQuery query);

    /**
     * 统计审批实例数量。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(ApprovalInstanceQuery query);

    /**
     * 按主键查询审批实例。
     *
     * @param id 主键
     * @return 审批实例
     */
    Optional<ApprovalInstance> findById(Long id);

    /**
     * 按业务单据查询审批实例。
     *
     * @param businessType 业务类型
     * @param businessId 业务单据ID
     * @return 审批实例
     */
    Optional<ApprovalInstance> findByBusiness(String businessType, Long businessId);

    /**
     * 新增审批实例；业务单据已有实例时不重复写入。
     *
     * @param instance 审批实例
     * @return 新增返回 {@code true}，已存在返回 {@code false}
     */
    boolean insertIfAbsent(ApprovalInstance instance);

    /**
     * 更新审批实例。
     *
     * @param instance 审批实例
     */
    void update(ApprovalInstance instance);
}
