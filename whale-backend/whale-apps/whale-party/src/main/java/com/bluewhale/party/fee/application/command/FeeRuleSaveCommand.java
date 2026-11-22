package com.bluewhale.party.fee.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 党费标准保存命令。
 *
 * @author BlueWhale
 */
@Data
public class FeeRuleSaveCommand {

    /** 档位名称 */
    @NotBlank(message = "{validation.party.fee-rule.rule-name-required}")
    @Size(max = 64, message = "{validation.party.fee-rule.rule-name-size}")
    private String ruleName;

    /** 基数下限（含） */
    @NotNull(message = "{validation.party.fee-rule.min-base-required}")
    private BigDecimal minBase;

    /** 基数上限（不含，空表示无上限） */
    private BigDecimal maxBase;

    /** 缴费比例 */
    @NotNull(message = "{validation.party.fee-rule.rate-required}")
    private BigDecimal rate;

    /** 状态：ACTIVE / INACTIVE */
    private String status;

    /** 排序号 */
    private Integer sortNo;

    /** 备注 */
    @Size(max = 255, message = "{validation.party.fee-rule.remark-size}")
    private String remark;
}
