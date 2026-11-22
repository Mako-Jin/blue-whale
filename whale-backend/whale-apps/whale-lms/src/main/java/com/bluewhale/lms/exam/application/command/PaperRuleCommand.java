package com.bluewhale.lms.exam.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 单条抽题规则。
 *
 * <p>条件为空表示不限；{@code score} 为空时取题目默认分值。</p>
 *
 * @author BlueWhale
 */
@Data
public class PaperRuleCommand {

    /** 分类 */
    private String category;

    /** 题型 */
    private String type;

    /** 难度 */
    private String difficulty;

    /** 抽取数量 */
    @NotNull(message = "{validation.lms.paper-rule.count-required}")
    @Min(value = 1, message = "{validation.lms.paper-rule.count-min}")
    @Max(value = 100, message = "{validation.lms.paper-rule.count-max}")
    private Integer count;

    /** 每题分值（为空取题目默认分值） */
    @DecimalMin(value = "0.1", message = "{validation.lms.paper-rule.score-min}")
    private BigDecimal score;
}
