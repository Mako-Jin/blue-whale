package com.bluewhale.crm.commerce.application.view;

import com.bluewhale.crm.commerce.domain.model.ArSnapshot;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户应收快照视图。
 *
 * @author BlueWhale
 */
@Data
public class ArSnapshotView {

    /** 快照 ID */
    private Long id;

    /** 客户 ID */
    private Long customerId;

    /** 客户编码 */
    private String customerCode;

    /** 客户名称 */
    private String customerName;

    /** 快照日期 */
    private LocalDate snapshotDate;

    /** 币种 */
    private String currency;

    /** 未结应收单数量 */
    private Integer receivableCount;

    /** 未结余额合计 */
    private BigDecimal totalRemaining;

    /** 未到期 */
    private BigDecimal notDue;

    /** 逾期 1-30 天 */
    private BigDecimal overdue1To30;

    /** 逾期 31-60 天 */
    private BigDecimal overdue31To60;

    /** 逾期 61-90 天 */
    private BigDecimal overdue61To90;

    /** 逾期 90 天以上 */
    private BigDecimal overdueOver90;

    /** 数据来源 */
    private String source;

    /** 最近同步时间 */
    private LocalDateTime lastSyncAt;

    /**
     * 由快照构建视图。
     *
     * @param snapshot 快照
     * @return 视图
     */
    public static ArSnapshotView from(ArSnapshot snapshot) {
        ArSnapshotView view = new ArSnapshotView();
        view.setId(snapshot.getId());
        view.setCustomerId(snapshot.getCustomerId());
        view.setCustomerCode(snapshot.getCustomerCode());
        view.setCustomerName(snapshot.getCustomerName());
        view.setSnapshotDate(snapshot.getSnapshotDate());
        view.setCurrency(snapshot.getCurrency());
        view.setReceivableCount(snapshot.getReceivableCount());
        view.setTotalRemaining(snapshot.getTotalRemaining());
        view.setNotDue(snapshot.getNotDue());
        view.setOverdue1To30(snapshot.getOverdue1To30());
        view.setOverdue31To60(snapshot.getOverdue31To60());
        view.setOverdue61To90(snapshot.getOverdue61To90());
        view.setOverdueOver90(snapshot.getOverdueOver90());
        view.setSource(snapshot.getSource());
        view.setLastSyncAt(snapshot.getLastSyncAt());
        return view;
    }
}
