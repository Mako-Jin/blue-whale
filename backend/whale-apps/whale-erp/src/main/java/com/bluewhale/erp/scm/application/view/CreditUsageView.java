package com.bluewhale.erp.scm.application.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 客户信用占用视图。
 *
 * <p>占用 = 未结应收 + 在途预占；额度为 0 时视为不限，可用额度为 {@code null}。</p>
 *
 * @author BlueWhale
 */
@Data
public class CreditUsageView {

    /** 客户主键 */
    private Long customerId;

    /** 客户名称 */
    private String customerName;

    /** 信用额度（0 表示不限） */
    private BigDecimal creditLimit;

    /** 未结应收 */
    private BigDecimal outstanding;

    /** 在途预占（已审核/部分发货订单未发货金额） */
    private BigDecimal reserved;

    /** 已占用合计 */
    private BigDecimal occupied;

    /** 可用额度（不限时为 null，超额时为负数） */
    private BigDecimal available;

    /** 是否已超额 */
    private Boolean exceeded;

    /** 是否不限额度 */
    private Boolean unlimited;
}
