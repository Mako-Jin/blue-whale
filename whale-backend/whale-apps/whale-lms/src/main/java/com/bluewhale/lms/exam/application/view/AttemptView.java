package com.bluewhale.lms.exam.application.view;

import com.bluewhale.lms.exam.domain.model.ExamAttempt;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 作答记录视图（我的考试 / 待判分队列共用）。
 *
 * @author BlueWhale
 */
@Data
public class AttemptView {

    /** 主键 */
    private Long id;

    /** 考试 ID */
    private Long examId;

    /** 考试名称 */
    private String examTitle;

    /** 试卷 ID（快照） */
    private Long paperId;

    /** 学员用户 ID */
    private Long userId;

    /** 学员用户名 */
    private String username;

    /** 第几次作答 */
    private Integer attemptNo;

    /** 开始作答时间 */
    private LocalDateTime startedAt;

    /** 本次截止时间 */
    private LocalDateTime deadlineAt;

    /** 交卷时间 */
    private LocalDateTime submittedAt;

    /** 状态 */
    private String status;

    /** 客观题得分 */
    private BigDecimal objectiveScore;

    /** 主观题得分 */
    private BigDecimal subjectiveScore;

    /** 总分 */
    private BigDecimal totalScore;

    /** 是否及格 */
    private Boolean passed;

    /** 切屏次数 */
    private Integer switchCount;

    /** 待人工判分题数（列表页提示用） */
    private Long pendingGradeCount;

    /**
     * 领域转视图。
     *
     * @param attempt 作答记录
     * @return 视图
     */
    public static AttemptView from(ExamAttempt attempt) {
        AttemptView view = new AttemptView();
        view.setId(attempt.getId());
        view.setExamId(attempt.getExamId());
        view.setPaperId(attempt.getPaperId());
        view.setUserId(attempt.getUserId());
        view.setUsername(attempt.getUsername());
        view.setAttemptNo(attempt.getAttemptNo());
        view.setStartedAt(attempt.getStartedAt());
        view.setDeadlineAt(attempt.getDeadlineAt());
        view.setSubmittedAt(attempt.getSubmittedAt());
        view.setStatus(attempt.getStatus() == null ? null : attempt.getStatus().name());
        view.setObjectiveScore(attempt.getObjectiveScore());
        view.setSubjectiveScore(attempt.getSubjectiveScore());
        view.setTotalScore(attempt.getTotalScore());
        view.setPassed(attempt.getPassed());
        view.setSwitchCount(attempt.getSwitchCount());
        return view;
    }
}
