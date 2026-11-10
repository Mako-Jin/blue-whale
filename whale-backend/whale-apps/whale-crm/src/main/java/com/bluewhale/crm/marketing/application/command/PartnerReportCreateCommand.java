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
    @NotNull(message = "渠道伙伴不能为空")
    private Long partnerId;

    /** CRM 客户 ID */
    @NotNull(message = "客户不能为空")
    private Long customerId;

    /** 项目名称 */
    @NotBlank(message = "项目名称不能为空")
    private String projectName;

    /** 预计金额 */
    @DecimalMin(value = "0", message = "预计金额不得为负数")
    private BigDecimal estimatedAmount;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;
}
