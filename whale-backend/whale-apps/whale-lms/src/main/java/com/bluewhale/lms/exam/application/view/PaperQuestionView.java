package com.bluewhale.lms.exam.application.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 试卷题目视图。
 *
 * @author BlueWhale
 */
@Data
public class PaperQuestionView {

    /** 题目 ID */
    private Long questionId;

    /** 题目编码 */
    private String questionCode;

    /** 题型 */
    private String type;

    /** 题干 */
    private String stem;

    /** 本题分值 */
    private BigDecimal score;

    /** 题序 */
    private Integer sortNo;

    /** 来源：MANUAL / RULE */
    private String source;
}
