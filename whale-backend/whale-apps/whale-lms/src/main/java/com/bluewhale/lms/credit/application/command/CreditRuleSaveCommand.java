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
    @NotBlank(message = "规则编码不能为空")
    @Size(max = 64, message = "规则编码不得超过 64 字符")
    private String ruleCode;

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 128, message = "规则名称不得超过 128 字符")
    private String name;

    /** 来源类型：COURSE / EXAM / TRAINING */
    @NotBlank(message = "学分来源不能为空")
    private String sourceType;

    /** 匹配对象 ID（0 或不传表示该来源全部） */
    private Long matchId;

    /** 单次学分 */
    @DecimalMin(value = "0.1", message = "单次学分必须大于 0")
    private BigDecimal credit;

    /** 备注 */
    @Size(max = 500, message = "备注不得超过 500 字符")
    private String remark;
}
