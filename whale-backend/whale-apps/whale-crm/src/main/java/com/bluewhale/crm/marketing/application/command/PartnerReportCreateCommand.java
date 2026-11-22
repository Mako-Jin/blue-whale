package com.bluewhale.crm.marketing.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新建渠道报单命令。
 *
 * @author BlueWhale
 */
@Data
public class PartnerReportCreateCommand {

    /** 渠道伙伴 ID */
    @NotNull(message = "{validation.crm.partner-report.partner-id-required}")
    private Long partnerId;

    /** CRM 客户 ID */
    @NotNull(message = "{validation.crm.partner-report.customer-id-required}")
    private Long customerId;

    /** 项目名称 */
    @NotBlank(message = "{validation.crm.partner-report.project-name-required}")
    private String projectName;

    /** 预计金额 */
    @DecimalMin(value = "0", message = "{validation.crm.partner-report.estimated-amount-min}")
    private BigDecimal estimatedAmount;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;
}
