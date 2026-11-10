package com.bluewhale.party.fee.application.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 欠缴账单（党费催缴通知扫描用）。
 *
 * @author BlueWhale
 */
@Data
public class FeeBillNoticeView {

    /** 账单 ID */
    private Long billId;

    /** 党员档案 ID */
    private Long memberId;

    /** 党员姓名快照 */
    private String memberName;

    /** 所属月份 yyyyMM */
    private String periodMonth;

    /** 应缴金额 */
    private BigDecimal dueAmount;

    /** 欠缴金额 */
    private BigDecimal outstandingAmount;
}
