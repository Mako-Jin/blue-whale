package com.bluewhale.crm.commerce.domain.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 客户应收汇总（ERP 只读契约）。
 *
 * @param customerId ERP 客户 ID
 * @param customerCode 客户编码
 * @param customerName 客户名称
 * @param currency 币种
 * @param receivableCount 未结应收单数量
 * @param totalRemaining 未结余额合计
 * @param notDue 未到期
 * @param overdue1To30 逾期 1-30 天
 * @param overdue31To60 逾期 31-60 天
 * @param overdue61To90 逾期 61-90 天
 * @param overdueOver90 逾期 90 天以上
 * @param items 未结应收明细
 * @author BlueWhale
 */
public record ErpReceivableSummary(Long customerId, String customerCode, String customerName, String currency,
                                   Integer receivableCount, BigDecimal totalRemaining, BigDecimal notDue,
                                   BigDecimal overdue1To30, BigDecimal overdue31To60, BigDecimal overdue61To90,
                                   BigDecimal overdueOver90, List<ErpReceivableItem> items) {

    /**
     * 是否为空摘要（ERP 未找到客户）。
     *
     * @return 客户 ID 为空返回 true
     */
    public boolean empty() {
        return customerId == null;
    }

    /**
     * 未结明细项。
     *
     * @param docNo 应收单号
     * @param docDate 单据日期
     * @param dueDate 到期日
     * @param currency 币种
     * @param amount 应收金额
     * @param settledAmount 已核销金额
     * @param remainingAmount 未结余额
     * @param status 状态
     * @author BlueWhale
     */
    public record ErpReceivableItem(String docNo, LocalDate docDate, LocalDate dueDate, String currency,
                                    BigDecimal amount, BigDecimal settledAmount, BigDecimal remainingAmount,
                                    String status) {
    }
}
