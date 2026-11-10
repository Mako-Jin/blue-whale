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
    @NotNull(message = "报价必须关联商机")
    private Long opportunityId;

    /** 报价标题 */
    @NotBlank(message = "报价标题不能为空")
    @Size(max = 128, message = "报价标题最多 128 个字符")
    private String title;

    /** 币种 */
    @Size(max = 8, message = "币种最多 8 个字符")
    private String currency = "CNY";

    /** 有效期 */
    private LocalDate validUntil;

    /** 整单折扣金额 */
    @DecimalMin(value = "0", message = "整单折扣不能为负数")
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** 备注 */
    @Size(max = 500, message = "备注最多 500 个字符")
    private String remark;

    /** 报价行 */
    @NotEmpty(message = "报价至少需要一行明细")
    @Valid
    private List<QuoteLineCommand> lines = new ArrayList<>();
}
