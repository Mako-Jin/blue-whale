package com.bluewhale.oa.approval.application.service;

import com.bluewhale.oa.approval.application.view.ApprovalEffectView;

import java.util.List;

/**
 * 审批业务副作用应用服务。
 *
 * @author BlueWhale
 */
public interface ApprovalEffectAppService {

    /**
     * 查询审批实例的副作用记录。
     *
     * @param approvalInstanceId 审批实例ID
     * @return 副作用视图列表
     */
    List<ApprovalEffectView> list(Long approvalInstanceId);

    /**
     * 登记副作用（幂等：同实例同类型只登记一次）。
     *
     * @param approvalInstanceId 审批实例ID
     * @param effectType 副作用类型
     * @param payload 执行参数（JSON）
     * @return 新增返回 {@code true}，已存在返回 {@code false}
     */
    boolean claim(Long approvalInstanceId, String effectType, String payload);

    /**
     * 标记副作用执行完成。
     *
     * @param approvalInstanceId 审批实例ID
     * @param effectType 副作用类型
     */
    void markDone(Long approvalInstanceId, String effectType);

    /**
     * 标记副作用执行失败。
     *
     * @param approvalInstanceId 审批实例ID
     * @param effectType 副作用类型
     * @param error 失败原因
     */
    void markFailed(Long approvalInstanceId, String effectType, String error);
}
