package com.bluewhale.lms.exam.application.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 交卷后的单题结果（学员视角）。
 *
 * <p>只回传「对/错」布尔值而不回传标准答案：学员需要知道哪题丢了分，
 * 但把标准答案全量发给前端等于把题库公开，补考、重考都会失效。
 * 标准答案只在教师判分视图中出现。</p>
 *
 * @author BlueWhale
 */
@Data
public class ResultQuestionView {

    /** 题目 ID */
    private Long questionId;

    /** 题型 */
    private String type;

    /** 题干 */
    private String stem;

    /** 本题满分 */
    private BigDecimal maxScore;

    /** 本题得分 */
    private BigDecimal score;

    /** 学员作答 */
    private String answer;

    /** 是否正确（主观题为空） */
    private Boolean correct;

    /** 是否已判（主观题待判时为 false） */
    private boolean graded;
}
