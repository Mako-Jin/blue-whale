package com.bluewhale.lms.exam.application.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 规则抽题结果。
 *
 * @author BlueWhale
 */
@Data
public class PaperGenerateResultView {

    /** 本次抽取题目数 */
    private int picked;

    /** 试卷题目总数（含手工题） */
    private int questionCount;

    /** 试卷总分（重算后） */
    private BigDecimal totalScore;

    /** 实际使用的种子 */
    private String seed;
}
