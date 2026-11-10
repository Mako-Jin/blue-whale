package com.bluewhale.oa.approval.domain.repository;

import com.bluewhale.oa.approval.domain.model.ApprovalConfig;
import com.bluewhale.oa.approval.domain.model.ApprovalConfigQuery;

import java.util.List;
import java.util.Optional;

/**
 * 审批配置仓储。
 *
 * @author BlueWhale
 */
public interface ApprovalConfigRepository {

    /**
     * 分页查询配置。
     *
     * @param query 查询条件
     * @return 当前页配置
     */
    List<ApprovalConfig> page(ApprovalConfigQuery query);

    /**
     * 统计配置数量。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(ApprovalConfigQuery query);

    /**
     * 按主键查询配置。
     *
     * @param id 主键
     * @return 配置
     */
    Optional<ApprovalConfig> findById(Long id);

    /**
     * 按业务类型查询配置。
     *
     * @param businessType 业务类型
     * @return 配置
     */
    Optional<ApprovalConfig> findByBusinessType(String businessType);

    /**
     * 新增配置。
     *
     * @param config 配置
     */
    void insert(ApprovalConfig config);

    /**
     * 更新配置。
     *
     * @param config 配置
     */
    void update(ApprovalConfig config);
}
