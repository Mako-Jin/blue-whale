package com.bluewhale.lms.exam.application.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 判分明细视图（教师视角，含标准答案）。
 *
 * @author BlueWhale
 */
@Data
public class GradeAnswerView {

    /** 题目 ID */
    private Long questionId;

    /** 题型 */
    private String type;

    /** 题干 */
    private String stem;

    /** 标准答案（仅教师判分可见） */
    private String standardAnswer;

    /** 学员作答 */
    private String answer;

    /** 本题满分 */
    private BigDecimal maxScore;

    /** 当前得分 */
    private BigDecimal score;

    /** 是否已判 */
    private boolean graded;

    /** 是否客观题（客观题只读，已自动判分） */
    private boolean objective;
}
