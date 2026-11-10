package com.bluewhale.erp.finance.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 按客户应收汇总视图（CRM 应收对账只读契约）。
 *
 * <p>账龄区间与 {@link AgingView} 一致：未到期、1–30 天、31–60 天、61–90 天、90 天以上。</p>
 *
 * @author BlueWhale
 */
@Data
public class CustomerReceivableView {

    /** 客户 ID（ERP 侧） */
    private Long customerId;

    /** 客户编码 */
    private String customerCode;

    /** 客户名称 */
    private String customerName;

    /** 币种 */
    private String currency;

    /** 未结应收单数量 */
    private Integer receivableCount;

    /** 未结余额合计 */
    private BigDecimal totalRemaining;

    /** 未到期 */
    private BigDecimal notDue;

    /** 逾期 1–30 天 */
    private BigDecimal overdue1To30;

    /** 逾期 31–60 天 */
    private BigDecimal overdue31To60;

    /** 逾期 61–90 天 */
    private BigDecimal overdue61To90;

    /** 逾期 90 天以上 */
    private BigDecimal overdueOver90;

    /** 数据快照时间 */
    private LocalDateTime snapshotTime;

    /** 未结应收明细 */
    private List<ReceivableView> receivables = new ArrayList<>();
}
