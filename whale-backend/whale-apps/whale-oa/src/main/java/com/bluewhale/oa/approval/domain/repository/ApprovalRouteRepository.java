package com.bluewhale.oa.approval.domain.repository;

import com.bluewhale.oa.approval.domain.model.ApprovalRoute;

import java.util.List;

/**
 * 审批人快照仓储。
 *
 * @author BlueWhale
 */
public interface ApprovalRouteRepository {

    /**
     * 查询审批实例的审批人快照。
     *
     * @param approvalInstanceId 审批实例ID
     * @return 快照列表
     */
    List<ApprovalRoute> listByInstance(Long approvalInstanceId);

    /**
     * 批量写入审批人快照；唯一键冲突忽略。
     *
     * @param routes 快照列表
     */
    void insertAll(List<ApprovalRoute> routes);
}
