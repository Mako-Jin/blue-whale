package com.bluewhale.lms.exam.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 作答卷面视图（学员答题页）。
 *
 * @author BlueWhale
 */
@Data
public class AttemptPaperView {

    /** 作答记录 ID */
    private Long attemptId;

    /** 考试 ID */
    private Long examId;

    /** 考试名称 */
    private String examTitle;

    /** 试卷 ID（快照） */
    private Long paperId;

    /** 单次作答时长（分钟） */
    private Integer durationMinutes;

    /** 开始作答时间 */
    private LocalDateTime startedAt;

    /** 本次截止时间 */
    private LocalDateTime deadlineAt;

    /** 交卷时间 */
    private LocalDateTime submittedAt;

    /** 状态 */
    private String status;

    /** 剩余秒数（已交卷为 0） */
    private Long remainingSeconds;

    /** 试卷总分 */
    private BigDecimal totalScore;

    /** 及格分 */
    private BigDecimal passScore;

    /** 是否乱序（前端仅用于提示，勿自行排序） */
    private Boolean shuffle;

    /** 题目（按服务端给定的呈现次序） */
    private List<AttemptQuestionView> questions = new ArrayList<>();
}
