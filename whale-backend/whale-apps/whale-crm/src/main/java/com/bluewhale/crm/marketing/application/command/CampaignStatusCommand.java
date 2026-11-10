package com.bluewhale.crm.marketing.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 营销活动状态变更命令。
 *
 * @author BlueWhale
 */
@Data
public class CampaignStatusCommand {

    /** 目标状态：RUNNING / ENDED / CANCELLED */
    @NotBlank(message = "目标状态不能为空")
    private String status;
}
