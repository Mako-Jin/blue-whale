package com.bluewhale.lms.exam.application.support;

import com.bluewhale.lms.exam.domain.model.Exam;
import com.bluewhale.lms.exam.domain.model.ExamAnswer;
import com.bluewhale.lms.exam.domain.model.ExamAttempt;
import com.bluewhale.lms.exam.domain.model.ExamScore;
import com.bluewhale.lms.exam.domain.model.Paper;
import com.bluewhale.lms.exam.domain.model.PaperQuestion;
import com.bluewhale.lms.exam.domain.model.Question;
import com.bluewhale.lms.exam.domain.repository.PaperRepository;
import com.bluewhale.lms.exam.domain.repository.QuestionRepository;
import com.bluewhale.lms.exam.domain.repository.ScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 判分共用支撑：试卷分值、题目加载、分数汇总与成绩版本落库。
 *
 * <p>交卷自动判分与人工判分必须用同一套汇总口径，否则「客观 + 主观 ≠ 总分」
 * 这类偏差会在两次判分之间悄悄出现。凡是跨场景共享的计算都收在这里，
 * 应用服务只负责流程编排。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class ExamScoringSupport {

    private final PaperRepository paperRepository;

    private final QuestionRepository questionRepository;

    private final ScoreRepository scoreRepository;

    /**
     * 组装试卷各题分值。
     *
     * @param paperId 试卷 ID
     * @return 题目 ID 到分值的映射
     */
    public Map<Long, BigDecimal> paperScoreMap(Long paperId) {
        List<PaperQuestion> items = paperRepository.listQuestions(paperId);
        Map<Long, BigDecimal> scores = new HashMap<>(items.size());
        for (PaperQuestion item : items) {
            scores.put(item.getQuestionId(), item.getScore() == null ? BigDecimal.ZERO : item.getScore());
        }
        return scores;
    }

    /**
     * 批量加载题目（含选项）。
     *
     * @param questionIds 题目 ID 集合
     * @return 题目 ID 到题目的映射
     */
    public Map<Long, Question> loadQuestions(Collection<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Map.of();
        }
        return questionIds.stream()
                .distinct()
                .map(questionRepository::findById)
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(Question::getId, Function.identity(), (first, second) -> first));
    }

    /**
     * 汇总客观题得分。
     *
     * @param answers 作答明细
     * @return 客观题得分
     */
    public BigDecimal objectiveTotal(List<ExamAnswer> answers) {
        return answers.stream()
                .filter(item -> item.getQuestionType() != null && item.getQuestionType().isObjective())
                .filter(ExamAnswer::isGraded)
                .map(item -> item.getScore() == null ? BigDecimal.ZERO : item.getScore())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 汇总主观题得分。
     *
     * @param answers 作答明细
     * @return 主观题得分
     */
    public BigDecimal subjectiveTotal(List<ExamAnswer> answers) {
        return answers.stream()
                .filter(item -> item.getQuestionType() != null && !item.getQuestionType().isObjective())
                .filter(ExamAnswer::isGraded)
                .map(item -> item.getScore() == null ? BigDecimal.ZERO : item.getScore())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 是否仍有待判题目。
     *
     * @param answers 作答明细
     * @return 存在未判题目返回 true
     */
    public boolean hasPendingGrade(List<ExamAnswer> answers) {
        return answers.stream().anyMatch(item -> !item.isGraded());
    }

    /**
     * 取考试及格分（空值按 0 处理）。
     *
     * @param exam 考试
     * @return 及格分
     */
    public BigDecimal passScoreOf(Exam exam) {
        return exam.getPassScore() == null ? BigDecimal.ZERO : exam.getPassScore();
    }

    /**
     * 试卷总分（空值按 0 处理）。
     *
     * @param paper 试卷
     * @return 总分
     */
    public BigDecimal paperTotalOf(Paper paper) {
        return paper.getTotalScore() == null ? BigDecimal.ZERO : paper.getTotalScore();
    }

    /**
     * 写入新的成绩版本（版本号在既有基础上递增，历史版本保留）。
     *
     * @param attempt 作答记录（已回写分数）
     * @param gradedBy 判分人
     * @param remark 备注
     * @return 新成绩版本
     */
    public ExamScore writeScoreVersion(ExamAttempt attempt, Long gradedBy, String remark) {
        int versionNo = scoreRepository.listByAttempt(attempt.getId()).size() + 1;
        ExamScore score = new ExamScore();
        score.setAttemptId(attempt.getId());
        score.setExamId(attempt.getExamId());
        score.setUserId(attempt.getUserId());
        score.setVersionNo(versionNo);
        score.setObjectiveScore(attempt.getObjectiveScore() == null ? BigDecimal.ZERO : attempt.getObjectiveScore());
        score.setSubjectiveScore(attempt.getSubjectiveScore() == null ? BigDecimal.ZERO : attempt.getSubjectiveScore());
        score.setTotalScore(attempt.getTotalScore() == null ? BigDecimal.ZERO : attempt.getTotalScore());
        score.setPassed(Boolean.TRUE.equals(attempt.getPassed()));
        score.setGradedBy(gradedBy);
        score.setGradedAt(LocalDateTime.now());
        score.setRemark(remark);
        scoreRepository.save(score);
        return score;
    }
}
