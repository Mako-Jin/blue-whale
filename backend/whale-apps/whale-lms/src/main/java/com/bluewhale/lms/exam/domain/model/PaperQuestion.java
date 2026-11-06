package com.bluewhale.lms.exam.domain.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 试卷题目（试卷内的一题，含本题分值与题序）。
 *
 * @author BlueWhale
 */
@Data
public class PaperQuestion {

    /** 主键 */
    private Long id;

    /** 试卷 ID */
    private Long paperId;

    /** 题目 ID */
    private Long questionId;

    /** 本题分值 */
    private BigDecimal score;

    /** 题序（从 1 起） */
    private Integer sortNo;

    /** 来源：MANUAL / RULE */
    private String source;
}
