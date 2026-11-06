package com.bluewhale.lms.exam.application.view;

import com.bluewhale.lms.exam.domain.model.ExamScore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成绩视图（版本化）。
 *
 * @author BlueWhale
 */
@Data
public class ScoreView {

    /** 主键 */
    private Long id;

    /** 作答记录 ID */
    private Long attemptId;

    /** 考试 ID */
    private Long examId;

    /** 考试名称 */
    private String examTitle;

    /** 学员用户 ID */
    private Long userId;

    /** 版本号 */
    private Integer versionNo;

    /** 客观题得分 */
    private BigDecimal objectiveScore;

    /** 主观题得分 */
    private BigDecimal subjectiveScore;

    /** 总分 */
    private BigDecimal totalScore;

    /** 是否及格 */
    private Boolean passed;

    /** 判分人 */
    private Long gradedBy;

    /** 判分时间 */
    private LocalDateTime gradedAt;

    /** 判分备注 */
    private String remark;

    /**
     * 领域转视图。
     *
     * @param score 成绩
     * @return 视图
     */
    public static ScoreView from(ExamScore score) {
        ScoreView view = new ScoreView();
        view.setId(score.getId());
        view.setAttemptId(score.getAttemptId());
        view.setExamId(score.getExamId());
        view.setUserId(score.getUserId());
        view.setVersionNo(score.getVersionNo());
        view.setObjectiveScore(score.getObjectiveScore());
        view.setSubjectiveScore(score.getSubjectiveScore());
        view.setTotalScore(score.getTotalScore());
        view.setPassed(score.isPassed());
        view.setGradedBy(score.getGradedBy());
        view.setGradedAt(score.getGradedAt());
        view.setRemark(score.getRemark());
        return view;
    }
}
