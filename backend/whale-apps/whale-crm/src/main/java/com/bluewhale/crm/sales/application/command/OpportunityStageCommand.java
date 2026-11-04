package com.bluewhale.crm.sales.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商机阶段流转命令。
 *
 * @author BlueWhale
 */
@Data
public class OpportunityStageCommand {

    /** 目标阶段：PROPOSAL / NEGOTIATION / WON / LOST */
    @NotBlank(message = "目标阶段不能为空")
    private String stage;

    /** 输单原因（转 LOST 必填） */
    private String lostReason;

    /** 本次流转顺带更新金额（转 WON 可用） */
    private BigDecimal amount;
}
