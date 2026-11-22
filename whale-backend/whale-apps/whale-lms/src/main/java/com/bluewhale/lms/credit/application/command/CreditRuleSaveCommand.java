package com.bluewhale.lms.credit.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 学分规则保存命令。
 *
 * @author BlueWhale
 */
@Data
public class CreditRuleSaveCommand {

    /** 规则编码（租户内唯一，保存时统一转大写） */
    @NotBlank(message = "{validation.lms.credit-rule.rule-code-required}")
    @Size(max = 64, message = "{validation.lms.credit-rule.rule-code-size}")
    private String ruleCode;

    /** 规则名称 */
    @NotBlank(message = "{validation.lms.credit-rule.name-required}")
    @Size(max = 128, message = "{validation.lms.credit-rule.name-size}")
    private String name;

    /** 来源类型：COURSE / EXAM / TRAINING */
    @NotBlank(message = "{validation.lms.credit-rule.source-type-required}")
    private String sourceType;

    /** 匹配对象 ID（0 或不传表示该来源全部） */
    private Long matchId;

    /** 单次学分 */
    @DecimalMin(value = "0.1", message = "{validation.lms.credit-rule.credit-min}")
    private BigDecimal credit;

    /** 备注 */
    @Size(max = 500, message = "{validation.lms.credit-rule.remark-size}")
    private String remark;
}
