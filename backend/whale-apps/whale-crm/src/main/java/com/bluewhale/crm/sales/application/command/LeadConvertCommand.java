package com.bluewhale.crm.sales.application.command;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 线索转化命令。
 *
 * <p>未指定 {@code customerId} 时按线索信息新建客户；指定时直接挂接既有客户。</p>
 *
 * @author BlueWhale
 */
@Data
public class LeadConvertCommand {

    /** 既有客户 ID；为空则新建客户 */
    private Long customerId;

    /** 商机名称；为空默认「线索名称-商机」 */
    private String opportunityName;

    /** 商机金额 */
    private BigDecimal amount;

    /** 预计关闭日期 */
    private LocalDate expectedCloseDate;
}
