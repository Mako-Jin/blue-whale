package com.bluewhale.lms.exam.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.credit.application.service.CreditAppService;
import com.bluewhale.lms.credit.domain.model.CreditSource;
import com.bluewhale.lms.exam.application.command.GradeCommand;
import com.bluewhale.lms.exam.application.command.GradeItemCommand;
import com.bluewhale.lms.exam.application.service.GradeAppService;
import com.bluewhale.lms.exam.application.support.ExamScoringSupport;
import com.bluewhale.lms.exam.application.view.AttemptView;
import com.bluewhale.lms.exam.application.view.GradeAnswerView;
import com.bluewhale.lms.exam.application.view.GradeView;
import com.bluewhale.lms.exam.application.view.ScoreView;
import com.bluewhale.lms.exam.domain.model.AttemptQuery;
import com.bluewhale.lms.exam.domain.model.AttemptStatus;
import com.bluewhale.lms.exam.domain.model.Exam;
import com.bluewhale.lms.exam.domain.model.ExamAnswer;
import com.bluewhale.lms.exam.domain.model.ExamAttempt;
import com.bluewhale.lms.exam.domain.model.ExamScore;
import com.bluewhale.lms.exam.domain.model.Question;
import com.bluewhale.lms.exam.domain.repository.AttemptRepository;
import com.bluewhale.lms.exam.domain.repository.ExamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 人工判分应用服务实现。
 *
 * <p>三条判分纪律：</p>
 * <ol>
 *   <li><b>只判主观题</b>：客观题在交卷时按标准答案判完，允许在此改分等于让同一份
 *       答案出现两套口径。确有争议就走重判版本，留痕可追溯；</li>
 *   <li><b>分数不得超题分</b>：逐题比对试卷分值，越界直接拒绝。宁可报错让判分人
 *       重填，也不落一个「总分比满分高」的成绩；</li>
 *   <li><b>不覆盖历史</b>：每次提交写新版本，申诉时能看到每一版分数与判分人。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class GradeAppServiceImpl implements GradeAppService {

    private final AttemptRepository attemptRepository;

    private final ExamRepository examRepository;

    private final ExamScoringSupport scoringSupport;

    private final CreditAppService creditAppService;

    private final LmsProperties properties;

    @Override
    public PageResult<AttemptView> pending(AttemptQuery query, LmsActor actor) {
        assertGate();
        query.setStatus(AttemptStatus.SUBMITTED.name());
        long current = Math.max(query.getCurrent(), 1L);
        long size = Math.min(Math.max(query.getSize(), 1L), properties.getAttemptPageLimit());
        long total = attemptRepository.count(query);
        List<AttemptView> views = new ArrayList<>();
        for (ExamAttempt attempt : attemptRepository.page(query, (current - 1) * size, (int) size)) {
            AttemptView view = AttemptView.from(attempt);
            view.setPendingGradeCount(attemptRepository.countPendingGrade(attempt.getId()));
            examRepository.findById(attempt.getExamId()).ifPresent(exam -> view.setExamTitle(exam.getTitle()));
            views.add(view);
        }
        return PageResult.of(views, total, current, size);
    }

    @Override
    public GradeView detail(Long attemptId, LmsActor actor) {
        assertGate();
        ExamAttempt attempt = requireAttempt(attemptId);
        Exam exam = examRepository.findById(attempt.getExamId()).orElse(null);
        List<ExamAnswer> answers = attemptRepository.listAnswers(attemptId);
        Map<Long, Question> questions = scoringSupport.loadQuestions(
                answers.stream().map(ExamAnswer::getQuestionId).toList());
        Map<Long, BigDecimal> maxScores = scoringSupport.paperScoreMap(attempt.getPaperId());
        GradeView view = new GradeView();
        view.setAttemptId(attempt.getId());
        view.setExamId(attempt.getExamId());
        view.setExamTitle(exam == null ? null : exam.getTitle());
        view.setUserId(attempt.getUserId());
        view.setUsername(attempt.getUsername());
        view.setAttemptNo(attempt.getAttemptNo());
        view.setStatus(attempt.getStatus() == null ? null : attempt.getStatus().name());
        view.setSubmittedAt(attempt.getSubmittedAt());
        view.setSwitchCount(attempt.getSwitchCount());
        view.setObjectiveScore(attempt.getObjectiveScore());
        view.setSubjectiveScore(attempt.getSubjectiveScore());
        view.setTotalScore(attempt.getTotalScore());
        view.setPendingGradeCount(attemptRepository.countPendingGrade(attemptId));
        List<GradeAnswerView> items = new ArrayList<>();
        for (ExamAnswer answer : answers) {
            items.add(buildAnswerView(answer, questions.get(answer.getQuestionId()),
                    maxScores.getOrDefault(answer.getQuestionId(), BigDecimal.ZERO)));
        }
        view.setAnswers(items);
        return view;
    }

    @Override
    @Transactional
    public ScoreView grade(Long attemptId, GradeCommand command, LmsActor actor) {
        assertGate();
        Long operatorId = actor.requireUserId();
        ExamAttempt attempt = requireAttempt(attemptId);
        if (attempt.getStatus() == null || attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw LmsErrorCode.exception(LmsErrorCode.ATTEMPT_STATE_CONFLICT, "学员尚未交卷，暂不可判分");
        }
        if (attempt.getStatus() == AttemptStatus.EXPIRED) {
            throw LmsErrorCode.exception(LmsErrorCode.ATTEMPT_STATE_CONFLICT, "超时失效的作答不可判分");
        }
        if (command.getItems().size() > properties.getGradeItemLimit()) {
            throw LmsErrorCode.exception(LmsErrorCode.GRADE_INVALID,
                    "单次判分题目数不得超过 " + properties.getGradeItemLimit());
        }
        Exam exam = examRepository.findById(attempt.getExamId())
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.EXAM_NOT_FOUND));
        Map<Long, BigDecimal> maxScores = scoringSupport.paperScoreMap(attempt.getPaperId());
        for (GradeItemCommand item : command.getItems()) {
            applyItem(attemptId, item, maxScores);
        }
        List<ExamAnswer> answers = attemptRepository.listAnswers(attemptId);
        boolean allGraded = !scoringSupport.hasPendingGrade(answers);
        attempt.applyScores(scoringSupport.objectiveTotal(answers), scoringSupport.subjectiveTotal(answers),
                scoringSupport.passScoreOf(exam), allGraded);
        attemptRepository.save(attempt);
        ExamScore score = scoringSupport.writeScoreVersion(attempt, operatorId,
                command.getRemark() == null || command.getRemark().isBlank()
                        ? "人工判分" : command.getRemark().trim());
        accrueIfPassed(score, attempt);
        return ScoreView.from(score);
    }

    /**
     * 判分后及格自动记学分。
     *
     * <p>与交卷路径共用同一幂等键（来源 = 考试 ID），所以「交卷已记过、判分又记一次」
     * 不会产生第二条台账；主观题先交卷后判分的场景也只需在判分时补这一刀。</p>
     *
     * @param score 本次落库的成绩版本
     * @param attempt 作答记录
     */
    private void accrueIfPassed(ExamScore score, ExamAttempt attempt) {
        if (score == null || !score.isPassed() || score.getExamId() == null || attempt.getUserId() == null) {
            return;
        }
        creditAppService.accrueQuietly(CreditSource.EXAM, score.getExamId(), attempt.getUserId(),
                attempt.getUsername());
    }

    /**
     * 断言需求门禁通过。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed()) {
            throw LmsErrorCode.exception(LmsErrorCode.GATE_CLOSED);
        }
    }

    /**
     * 加载作答记录，不存在按 404 处理。
     *
     * @param attemptId 作答记录 ID
     * @return 作答记录
     */
    private ExamAttempt requireAttempt(Long attemptId) {
        return attemptRepository.findById(attemptId)
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.ATTEMPT_NOT_FOUND));
    }

    /**
     * 校验并落库单题判分。
     *
     * @param attemptId 作答记录 ID
     * @param item 判分明细
     * @param maxScores 试卷各题满分
     */
    private void applyItem(Long attemptId, GradeItemCommand item, Map<Long, BigDecimal> maxScores) {
        ExamAnswer answer = attemptRepository.findAnswer(attemptId, item.getQuestionId())
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.ANSWER_INVALID,
                        "题目 " + item.getQuestionId() + " 不属于本次作答"));
        if (answer.getQuestionType() == null || answer.getQuestionType().isObjective()) {
            throw LmsErrorCode.exception(LmsErrorCode.GRADE_NOT_PENDING,
                    "题目 " + item.getQuestionId() + " 为客观题，交卷时已自动判分");
        }
        BigDecimal max = maxScores.get(item.getQuestionId());
        if (max == null) {
            throw LmsErrorCode.exception(LmsErrorCode.GRADE_INVALID,
                    "题目 " + item.getQuestionId() + " 不在试卷内，无法判分");
        }
        if (item.getScore().compareTo(max) > 0) {
            throw LmsErrorCode.exception(LmsErrorCode.GRADE_INVALID,
                    "题目 " + item.getQuestionId() + " 得分 " + item.getScore().stripTrailingZeros().toPlainString()
                            + " 超过本题满分 " + max.stripTrailingZeros().toPlainString());
        }
        answer.setScore(item.getScore());
        answer.setCorrect(null);
        answer.setGraded(true);
        attemptRepository.saveAnswer(answer);
    }

    /**
     * 组装判分明细视图。
     *
     * @param answer 作答明细
     * @param question 题目
     * @param maxScore 本题满分
     * @return 判分明细视图
     */
    private GradeAnswerView buildAnswerView(ExamAnswer answer, Question question, BigDecimal maxScore) {
        GradeAnswerView view = new GradeAnswerView();
        view.setQuestionId(answer.getQuestionId());
        view.setType(answer.getQuestionType() == null ? null : answer.getQuestionType().name());
        view.setAnswer(answer.getAnswer());
        view.setScore(answer.getScore());
        view.setMaxScore(maxScore);
        view.setGraded(answer.isGraded());
        view.setObjective(answer.getQuestionType() != null && answer.getQuestionType().isObjective());
        if (question != null) {
            view.setStem(question.getStem());
            view.setStandardAnswer(question.getStandardAnswer());
        }
        return view;
    }
}
