package com.bluewhale.crm.commerce.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 报价保存命令（新建 / 编辑共用）。
 *
 * @author BlueWhale
 */
@Data
public class QuoteSaveCommand {

    /** 商机 ID */
    @NotNull(message = "{validation.crm.quote.opportunity-id-required}")
    private Long opportunityId;

    /** 报价标题 */
    @NotBlank(message = "{validation.crm.quote.title-required}")
    @Size(max = 128, message = "{validation.crm.quote.title-size}")
    private String title;

    /** 币种 */
    @Size(max = 8, message = "{validation.crm.quote.currency-size}")
    private String currency = "CNY";

    /** 有效期 */
    private LocalDate validUntil;

    /** 整单折扣金额 */
    @DecimalMin(value = "0", message = "{validation.crm.quote.discount-amount-min}")
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** 备注 */
    @Size(max = 500, message = "{validation.crm.quote.remark-size}")
    private String remark;

    /** 报价行 */
    @NotEmpty(message = "{validation.crm.quote.lines-required}")
    @Valid
    private List<QuoteLineCommand> lines = new ArrayList<>();
}
