package com.bluewhale.pay.mapper.stat;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 按天聚合的收款额。
 *
 * @author BlueWhale
 */
@Data
public class PayDayAmountPo {

    /** 日期（{@code DATE(pay_time)} 的输出，仅用于分组标签） */
    private LocalDate day;

    /** 当日收款金额 */
    private BigDecimal amount;
}
