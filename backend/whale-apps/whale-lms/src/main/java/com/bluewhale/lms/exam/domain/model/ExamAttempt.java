package com.bluewhale.lms.exam.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 考试作答记录（一人一次一条）。
 *
 * <p>边界口径：</p>
 * <ol>
 *   <li><b>考试窗外不可作答</b>：开始作答由 {@link Exam#assertOpen} 把关，
 *       保存与提交都要校验 {@link #deadlineAt}，过了截止一律拒绝；</li>
 *   <li><b>乱序不影响判分</b>：{@link #seed} 只决定呈现次序（写入作答明细的
 *       {@code sortNo}），判分按 {@code questionId} 与标准答案比对，与次序无关；</li>
 *   <li><b>成绩版本</b>：每次人工改分生成新的成绩版本，历史版本保留。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Data
public class ExamAttempt {

    /** 主键 */
    private Long id;

    /** 考试 ID */
    private Long examId;

    /** 试卷 ID（快照） */
    private Long paperId;

    /** 学员用户 ID */
    private Long userId;

    /** 学员用户名 */
    private String username;

    /** 第几次作答 */
    private Integer attemptNo;

    /** 乱序种子 */
    private String seed;

    /** 开始作答时间 */
    private LocalDateTime startedAt;

    /** 本次截止时间 */
    private LocalDateTime deadlineAt;

    /** 交卷时间 */
    private LocalDateTime submittedAt;

    /** 状态 */
    private AttemptStatus status;

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

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 断言仍在作答中。
     */
    public void assertInProgress() {
        if (status == null || !status.isOpen()) {
            throw LmsErrorCode.exception(LmsErrorCode.ATTEMPT_STATE_CONFLICT,
                    "该次作答已交卷或已失效，不可再修改答案");
        }
    }

    /**
     * 断言未超过本次截止时间。
     *
     * @param now 当前时刻
     */
    public void assertWithinDeadline(LocalDateTime now) {
        if (deadlineAt != null && now.isAfter(deadlineAt)) {
            throw LmsErrorCode.exception(LmsErrorCode.ATTEMPT_EXPIRED,
                    "已超过本次作答截止时间 " + deadlineAt);
        }
    }

    /**
     * 标记交卷。
     *
     * <p><b>截止后仍受理交卷，但按截止时刻记账。</b>学员浏览器崩了、断网了，
     * 若在截止后拒绝交卷，记录会永久锁在作答中，已保存的答案反而永远进不了
     * 判分队列——这比「晚交一分钟」严重得多。把 {@code submittedAt} 压到
     * {@link #deadlineAt}，既保住已存答案，也不给「拖到考后慢慢答」留空间。
     * 真正被禁止的是超时后继续改答案，见 {@link #assertWithinDeadline}。</p>
     *
     * @param now 交卷时刻
     */
    public void submit(LocalDateTime now) {
        assertInProgress();
        submittedAt = deadlineAt != null && now.isAfter(deadlineAt) ? deadlineAt : now;
        status = AttemptStatus.SUBMITTED;
    }

    /**
     * 回写判分结果与状态。
     *
     * @param objective 客观题得分
     * @param subjective 主观题得分
     * @param passScore 及格分
     * @param allGraded 是否全部题目已判
     */
    public void applyScores(BigDecimal objective, BigDecimal subjective, BigDecimal passScore, boolean allGraded) {
        objectiveScore = objective;
        subjectiveScore = subjective;
        totalScore = objective.add(subjective);
        passed = totalScore.compareTo(passScore) >= 0;
        if (allGraded) {
            status = AttemptStatus.GRADED;
        }
    }
}
