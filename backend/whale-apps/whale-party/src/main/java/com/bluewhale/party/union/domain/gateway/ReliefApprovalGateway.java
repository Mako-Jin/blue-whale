package com.bluewhale.party.union.domain.gateway;

/**
 * 慰问帮扶审批端口。
 *
 * <p>领域层只声明「发起审批」与「查询审批状态」两个能力，具体走 workflow 还是人工
 * 由基础设施实现决定，便于在审批服务不可用时降级而不把外部依赖渗进业务规则。</p>
 *
 * @author BlueWhale
 */
public interface ReliefApprovalGateway {

    /**
     * 发起审批实例。
     *
     * @param request 发起请求
     * @return 工作流实例 ID
     */
    Long start(ReliefApprovalRequest request);

    /**
     * 查询审批实例状态。
     *
     * @param instanceId 工作流实例 ID
     * @return 状态码：0 运行中 / 1 已通过 / 2 已驳回 / 3 已撤销
     */
    Integer queryStatus(Long instanceId);
}
