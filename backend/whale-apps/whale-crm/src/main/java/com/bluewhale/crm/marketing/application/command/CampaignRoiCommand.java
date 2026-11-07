package com.bluewhale.crm.marketing.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 营销活动 ROI 回填命令。
 *
 * @author BlueWhale
 */
@Data
public class CampaignRoiCommand {

    /** 归因线索数量 */
    @Min(value = 0, message = "归因线索数量不得为负数")
    private Integer attributedLeadCount;

    /** 归因赢单金额 */
    @DecimalMin(value = "0", message = "归因赢单金额不得为负数")
    private BigDecimal attributedWonAmount;

    /** 实际费用 */
    @DecimalMin(value = "0", message = "实际费用不得为负数")
    private BigDecimal actualCost;
}
