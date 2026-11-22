package com.bluewhale.crm.marketing.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 渠道报单冲突裁决命令。
 *
 * @author BlueWhale
 */
@Data
public class PartnerReportResolveCommand {

    /** 裁决结果：APPROVED / REJECTED */
    @NotBlank(message = "{validation.crm.partner-report-resolve.status-required}")
    private String status;

    /** 核准金额（APPROVED 必填） */
    @DecimalMin(value = "0", message = "{validation.crm.partner-report-resolve.approved-amount-min}")
    private BigDecimal approvedAmount;
}
