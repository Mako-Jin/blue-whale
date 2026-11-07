package com.bluewhale.crm.marketing.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 新建营销活动命令。
 *
 * @author BlueWhale
 */
@Data
public class CampaignCreateCommand {

    /** 活动名称 */
    @NotBlank(message = "活动名称不能为空")
    private String campaignName;

    /** 活动类型 */
    private String campaignType;

    /** 主要渠道 */
    private String channel;

    /** 预算 */
    @DecimalMin(value = "0", message = "预算不得为负数")
    private BigDecimal budget;

    /** 计划开始日期 */
    private LocalDate plannedStart;

    /** 计划结束日期 */
    private LocalDate plannedEnd;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;
}
