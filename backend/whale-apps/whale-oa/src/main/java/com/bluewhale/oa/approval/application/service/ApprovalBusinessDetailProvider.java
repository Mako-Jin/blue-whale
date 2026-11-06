package com.bluewhale.oa.approval.application.service;

import com.bluewhale.oa.approval.application.view.ApprovalBusinessDetailView;

/**
 * 审批业务详情提供者 SPI。
 *
 * <p>各业务上下文实现本接口，向审批中心输出白名单业务字段；找不到业务时返回 {@code null}。</p>
 *
 * @author BlueWhale
 */
public interface ApprovalBusinessDetailProvider {

    /**
     * 是否支持该业务类型。
     *
     * @param businessType 业务类型
     * @return 支持返回 {@code true}
     */
    boolean supports(String businessType);

    /**
     * 查询业务详情。
     *
     * @param businessType 业务类型
     * @param businessId 业务单据ID
     * @return 业务详情；不存在返回 {@code null}
     */
    ApprovalBusinessDetailView detail(String businessType, Long businessId);
}
