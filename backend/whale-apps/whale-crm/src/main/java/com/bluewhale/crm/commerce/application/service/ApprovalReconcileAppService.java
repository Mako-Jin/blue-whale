package com.bluewhale.crm.commerce.application.service;

/**
 * 审批结果对账应用服务。
 *
 * <p>whale-workflow 实例办结后没有主动回调实现，PENDING 单据由本服务定时拉取
 * workflow 内部批量状态接口并对已办结实例触发幂等回调，兜住回调缺失、
 * 服务重启等场景。</p>
 *
 * @author BlueWhale
 */
public interface ApprovalReconcileAppService {

    /**
     * 执行一轮审批结果对账。
     *
     * @return 本次实际推进状态的单据数
     */
    int reconcileAll();
}
