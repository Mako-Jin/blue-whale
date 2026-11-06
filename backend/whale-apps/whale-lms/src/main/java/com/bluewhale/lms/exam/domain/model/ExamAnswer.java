package com.bluewhale.lms.exam.domain.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 作答明细（一题一条）。
 *
 * @author BlueWhale
 */
@Data
public class ExamAnswer {

    /** 主键 */
    private Long id;

    /** 作答记录 ID */
    private Long attemptId;

    /** 题目 ID */
    private Long questionId;

    /** 题型快照 */
    private QuestionType questionType;

    /** 学员作答（已归一） */
    private String answer;

    /** 是否正确（主观题为空） */
    private Boolean correct;

    /** 本题得分 */
    private BigDecimal score;

    /** 是否已判 */
    private boolean graded;

    /** 呈现次序 */
    private Integer sortNo;

    /**
     * 是否待人工判分。
     *
     * @return 未判且客观题为 false
     */
    public boolean isPendingManualGrade() {
        return !graded && questionType != null && !questionType.isObjective();
    }
}
