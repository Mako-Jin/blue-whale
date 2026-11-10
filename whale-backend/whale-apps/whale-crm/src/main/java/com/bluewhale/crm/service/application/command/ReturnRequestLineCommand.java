package com.bluewhale.crm.service.application.command;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 退换货申请行命令。
 *
 * @author BlueWhale
 */
@Data
public class ReturnRequestLineCommand {

    /** 来源订单行号 */
    @NotNull
    private Integer orderLineNo;

    /** 退货数量 */
    @NotNull
    private BigDecimal quantity;
}
