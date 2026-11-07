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
    @NotBlank(message = "裁决结果不能为空")
    private String status;

    /** 核准金额（APPROVED 必填） */
    @DecimalMin(value = "0", message = "核准金额不得为负数")
    private BigDecimal approvedAmount;
}
