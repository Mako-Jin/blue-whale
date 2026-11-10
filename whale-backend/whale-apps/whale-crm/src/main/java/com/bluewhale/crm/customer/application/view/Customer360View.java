package com.bluewhale.crm.customer.application.view;

import com.bluewhale.crm.customer.domain.model.CustomerOpportunitySummary;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户 360 视图。
 *
 * <p>本期聚合客户档案、联系人、画像、最近跟进、商机摘要与负责人变更；
 * 合同、订单、回款、工单依赖跨服务联调，未联通时在 {@link #unavailableIntegrations}
 * 中明确声明，避免前端展示假数据。</p>
 *
 * @author BlueWhale
 */
@Data
public class Customer360View {

    /** 客户档案 */
    private CustomerView customer;

    /** 联系人 */
    private List<ContactView> contacts;

    /** 画像 */
    private CustomerProfileView profile;

    /** 最近跟进 */
    private List<FollowUpView> recentFollowUps;

    /** 商机摘要 */
    private CustomerOpportunitySummary opportunitySummary;

    /** 负责人变更历史 */
    private List<OwnerLogView> ownerLogs;

    /** 暂不可用的跨服务数据 */
    private List<String> unavailableIntegrations;

    /** 生成时间 */
    private LocalDateTime generatedAt;
}
