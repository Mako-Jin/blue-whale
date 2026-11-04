package com.bluewhale.oa.approval.domain.repository;

import com.bluewhale.oa.approval.domain.model.ApprovalRouteRule;

import java.util.List;
import java.util.Optional;

/**
 * 审批人解析规则仓储。
 *
 * @author BlueWhale
 */
public interface ApprovalRouteRuleRepository {

    /**
     * 查询业务类型下的全部规则。
     *
     * @param businessType 业务类型
     * @return 规则列表
     */
    List<ApprovalRouteRule> listByBusinessType(String businessType);

    /**
     * 按主键查询规则。
     *
     * @param id 主键
     * @return 规则
     */
    Optional<ApprovalRouteRule> findById(Long id);

    /**
     * 按业务类型与节点查询规则。
     *
     * @param businessType 业务类型
     * @param nodeKey 节点标识
     * @return 规则
     */
    Optional<ApprovalRouteRule> findByBusinessAndNode(String businessType, String nodeKey);

    /**
     * 新增规则。
     *
     * @param rule 规则
     */
    void insert(ApprovalRouteRule rule);

    /**
     * 更新规则。
     *
     * @param rule 规则
     */
    void update(ApprovalRouteRule rule);

    /**
     * 删除规则（逻辑删除）。
     *
     * @param id 主键
     */
    void deleteById(Long id);
}
