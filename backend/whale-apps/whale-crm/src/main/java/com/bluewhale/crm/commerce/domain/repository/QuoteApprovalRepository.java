package com.bluewhale.crm.commerce.domain.repository;

import com.bluewhale.crm.commerce.domain.model.QuoteApproval;
import com.bluewhale.crm.commerce.domain.model.QuoteApprovalStatus;

import java.time.LocalDateTime;

/**
 * 报价审批记录仓储端口。
 *
 * @author BlueWhale
 */
public interface QuoteApprovalRepository {

    /**
     * 插入审批记录。
     *
     * @param approval 审批记录
     */
    void insert(QuoteApproval approval);

    /**
     * 按流程实例查询待决策记录。
     *
     * @param instanceId 流程实例 ID
     * @return 待决策记录；不存在返回 {@code null}
     */
    QuoteApproval findPendingByInstanceId(Long instanceId);

    /**
     * 条件标记记录为终态（仅 PENDING 命中）。
     *
     * @param id 审批记录 ID
     * @param status 终态
     * @param comment 审批意见
     * @param at 决策时间
     * @return 更新成功返回 true
     */
    boolean markDecided(Long id, QuoteApprovalStatus status, String comment, LocalDateTime at);
}
