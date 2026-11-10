package com.bluewhale.lms.exam.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 判分视图（教师打开某份作答时看到的内容）。
 *
 * @author BlueWhale
 */
@Data
public class GradeView {

    /** 作答记录 ID */
    private Long attemptId;

    /** 考试 ID */
    private Long examId;

    /** 考试名称 */
    private String examTitle;

    /** 学员用户 ID */
    private Long userId;

    /** 学员用户名 */
    private String username;

    /** 第几次作答 */
    private Integer attemptNo;

    /** 状态 */
    private String status;

    /** 交卷时间 */
    private LocalDateTime submittedAt;

    /** 考场切屏次数 */
    private Integer switchCount;

    /** 客观题得分（已自动判定） */
    private BigDecimal objectiveScore;

    /** 主观题得分 */
    private BigDecimal subjectiveScore;

    /** 总分 */
    private BigDecimal totalScore;

    /** 待判题数 */
    private Long pendingGradeCount;

    /** 题目与作答明细 */
    private List<GradeAnswerView> answers = new ArrayList<>();
}
