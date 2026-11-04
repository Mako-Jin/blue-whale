package com.bluewhale.erp.analytics.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应收事实只读实体，对应只读视图 {@code v_erp_bi_receivable_fact}。
 *
 * @author BlueWhale
 */
@Data
@TableName("v_erp_bi_receivable_fact")
public class ReceivableFactEntity {

    /** 应收单号 */
    private String docNo;

    /** 客户名称 */
    private String customerName;

    /** 单据日期 */
    private LocalDate docDate;

    /** 到期日 */
    private LocalDate dueDate;

    /** 应收金额 */
    private BigDecimal amount;

    /** 已核销金额 */
    private BigDecimal settledAmount;

    /** 已计提坏账金额 */
    private BigDecimal badDebtAmount;

    /** 状态 */
    private String status;
}
