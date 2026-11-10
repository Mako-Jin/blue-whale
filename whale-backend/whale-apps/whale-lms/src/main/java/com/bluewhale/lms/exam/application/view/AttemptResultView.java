package com.bluewhale.lms.exam.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 交卷结果 / 成绩详情视图（学员视角）。
 *
 * @author BlueWhale
 */
@Data
public class AttemptResultView {

    /** 作答记录 ID */
    private Long attemptId;

    /** 考试 ID */
    private Long examId;

    /** 考试名称 */
    private String examTitle;

    /** 第几次作答 */
    private Integer attemptNo;

    /** 状态 */
    private String status;

    /** 客观题得分 */
    private BigDecimal objectiveScore;

    /** 主观题得分 */
    private BigDecimal subjectiveScore;

    /** 总分 */
    private BigDecimal totalScore;

    /** 及格分 */
    private BigDecimal passScore;

    /** 是否及格 */
    private Boolean passed;

    /** 交卷时间 */
    private LocalDateTime submittedAt;

    /** 是否有主观题待判 */
    private Boolean pendingGrade;

    /** 成绩版本号 */
    private Integer scoreVersion;

    /** 明细分值（按呈现次序） */
    private List<ResultQuestionView> questions = new ArrayList<>();
}
