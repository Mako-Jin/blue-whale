package com.bluewhale.erp.finance.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 凭证创建命令。
 *
 * @author BlueWhale
 */
@Data
public class VoucherCreateCommand {

    /** 凭证日期 */
    @NotNull(message = "凭证日期不能为空")
    private LocalDate voucherDate;

    /** 币种（默认 CNY） */
    private String currency;

    /** 汇率（默认 1） */
    private BigDecimal exchangeRate;

    /** 来源类型 */
    private String sourceType;

    /** 来源单据 ID */
    private String sourceId;

    /** 备注 */
    private String remark;

    /** 分录 */
    @Valid
    @NotEmpty(message = "凭证至少需要一条分录")
    private List<VoucherLineCommand> lines;
}
