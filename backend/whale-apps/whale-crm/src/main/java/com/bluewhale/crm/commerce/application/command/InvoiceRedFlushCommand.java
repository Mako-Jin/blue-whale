package com.bluewhale.crm.commerce.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 发票红冲命令。
 *
 * @author BlueWhale
 */
@Data
public class InvoiceRedFlushCommand {

    /** 红冲金额（正数） */
    @NotNull
    private BigDecimal amount;

    /** 红冲原因 */
    @NotBlank
    private String reason;
}
