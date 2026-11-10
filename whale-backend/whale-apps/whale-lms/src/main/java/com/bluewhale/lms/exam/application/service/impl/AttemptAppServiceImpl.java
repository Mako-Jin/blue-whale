package com.bluewhale.lms.exam.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.credit.application.service.CreditAppService;
import com.bluewhale.lms.credit.domain.model.CreditSource;
import com.bluewhale.lms.exam.application.command.AttemptAnswerCommand;
import com.bluewhale.lms.exam.application.command.AttemptSaveCommand;
import com.bluewhale.lms.exam.application.service.AttemptAppService;
import com.bluewhale.lms.exam.application.support.ExamScoringSupport;
import com.bluewhale.lms.exam.application.view.AttemptPaperView;
import com.bluewhale.lms.exam.application.view.AttemptQuestionView;
import com.bluewhale.lms.exam.application.view.AttemptResultView;
import com.bluewhale.lms.exam.application.view.AttemptView;
import com.bluewhale.lms.exam.application.view.ExamMetricsView;
import com.bluewhale.lms.exam.application.view.ResultQuestionView;
import com.bluewhale.lms.exam.application.view.ScoreView;
import com.bluewhale.lms.exam.domain.model.AttemptQuery;
import com.bluewhale.lms.exam.domain.model.AttemptStatus;
import com.bluewhale.lms.exam.domain.model.Exam;
import com.bluewhale.lms.exam.domain.model.ExamAnswer;
import com.bluewhale.lms.exam.domain.model.ExamAttempt;
import com.bluewhale.lms.exam.domain.model.ExamScore;
import com.bluewhale.lms.exam.domain.model.Judge;
import com.bluewhale.lms.exam.domain.model.Paper;
import com.bluewhale.lms.exam.domain.model.PaperQuestion;
import com.bluewhale.lms.exam.domain.model.PickOrder;
import com.bluewhale.lms.exam.domain.model.Question;
import com.bluewhale.lms.exam.domain.model.QuestionType;
import com.bluewhale.lms.exam.domain.repository.AttemptRepository;
import com.bluewhale.lms.exam.domain.repository.ExamRepository;
import com.bluewhale.lms.exam.domain.repository.PaperRepository;
import com.bluewhale.lms.exam.domain.repository.ScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 作答应用服务实现。
 *
 * <p>判分与呈现次序彻底解耦，这是本类最重要的不变量：</p>
 * <ol>
 *   <li><b>题序只是展示</b>：开始作答时如果考试开启乱序，就按 {@link PickOrder#keyOf}
 *       从「考试编码 + 学员 + 第几次」派生的 seed 给题排序，结果写进作答明细的
 *       {@code sortNo}。同一份作答重复打开、服务重启，看到的题序都一样；</li>
 *   <li><b>判分只看题目 ID</b>：交卷时按 {@code questionId} 取标准答案比对，与
 *       {@code sortNo} 无任何关系。乱序因此不会制造判分风险；</li>
 *   <li><b>答案归一前置</b>：保存即归一（多选升序去重、判断 TRUE/FALSE），
 *       判分时只做字符串相等，不在判分路径上做容错，避免「同一答案两种判法」。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class AttemptAppServiceImpl implements AttemptAppService {

    private final ExamRepository examRepository;

    private final PaperRepository paperRepository;

    private final AttemptRepository attemptRepository;

    private final ScoreRepository scoreRepository;

    private final ExamScoringSupport scoringSupport;

    private final CreditAppService creditAppService;

    private final LmsProperties properties;

    @Override
    @Transactional
    public Long start(Long examId, LmsActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        LocalDateTime now = LocalDateTime.now();
        Exam exam = requireExam(examId);
        exam.assertOpen(now);
        Paper paper = paperRepository.findById(exam.getPaperId())
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.EXAM_INVALID, "考试引用的试卷不存在"));
        ExamAttempt inProgress = attemptRepository.findInProgress(examId, userId).orElse(null);
        if (inProgress != null) {
            return inProgress.getId();
        }
        long used = attemptRepository.countByExamAndUser(examId, userId);
        int maxAttempts = exam.getMaxAttempts() == null ? 1 : exam.getMaxAttempts();
        if (used >= maxAttempts) {
            throw LmsErrorCode.exception(LmsErrorCode.EXAM_ATTEMPT_LIMIT,
                    "本场考试最多作答 " + maxAttempts + " 次，已用尽");
        }
        ExamAttempt attempt = new ExamAttempt();
        attempt.setExamId(examId);
        attempt.setPaperId(paper.getId());
        attempt.setUserId(userId);
        attempt.setUsername(actor.getUsername());
        attempt.setAttemptNo((int) used + 1);
        attempt.setSeed(exam.getExamCode() + "#" + userId + "#" + (used + 1));
        attempt.setStartedAt(now);
        attempt.setDeadlineAt(exam.deadlineOf(now));
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        attempt.setSwitchCount(0);
        attemptRepository.save(attempt);
        buildAnswerSkeletons(attempt, exam, paper);
        return attempt.getId();
    }

    @Override
    public AttemptPaperView paper(Long attemptId, LmsActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        ExamAttempt attempt = requireOwnedAttempt(attemptId, userId);
        Exam exam = examRepository.findById(attempt.getExamId()).orElse(null);
        List<ExamAnswer> answers = attemptRepository.listAnswers(attemptId);
        Map<Long, Question> questions = scoringSupport.loadQuestions(
                answers.stream().map(ExamAnswer::getQuestionId).toList());
        Map<Long, BigDecimal> maxScores = scoringSupport.paperScoreMap(attempt.getPaperId());
        AttemptPaperView view = new AttemptPaperView();
        view.setAttemptId(attempt.getId());
        view.setExamId(attempt.getExamId());
        view.setExamTitle(exam == null ? null : exam.getTitle());
        view.setPaperId(attempt.getPaperId());
        view.setDurationMinutes(exam == null ? null : exam.getDurationMinutes());
        view.setStartedAt(attempt.getStartedAt());
        view.setDeadlineAt(attempt.getDeadlineAt());
        view.setSubmittedAt(attempt.getSubmittedAt());
        view.setStatus(attempt.getStatus() == null ? null : attempt.getStatus().name());
        view.setRemainingSeconds(remainingSeconds(attempt));
        view.setTotalScore(maxScores.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        view.setPassScore(exam == null ? BigDecimal.ZERO : scoringSupport.passScoreOf(exam));
        view.setShuffle(exam != null && exam.isShuffle());
        List<AttemptQuestionView> items = new ArrayList<>();
        for (ExamAnswer answer : answers) {
            items.add(buildQuestionView(answer, questions.get(answer.getQuestionId()),
                    maxScores.getOrDefault(answer.getQuestionId(), BigDecimal.ZERO)));
        }
        view.setQuestions(items);
        return view;
    }

    @Override
    @Transactional
    public int save(Long attemptId, AttemptSaveCommand command, LmsActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        ExamAttempt attempt = requireOwnedAttempt(attemptId, userId);
        attempt.assertInProgress();
        attempt.assertWithinDeadline(LocalDateTime.now());
        if (command.getAnswers() == null || command.getAnswers().isEmpty()) {
            return 0;
        }
        Map<Long, ExamAnswer> existing = new LinkedHashMap<>();
        for (ExamAnswer answer : attemptRepository.listAnswers(attemptId)) {
            existing.put(answer.getQuestionId(), answer);
        }
        int saved = 0;
        for (AttemptAnswerCommand item : command.getAnswers()) {
            ExamAnswer answer = existing.get(item.getQuestionId());
            if (answer == null) {
                throw LmsErrorCode.exception(LmsErrorCode.ANSWER_INVALID,
                        "题目 " + item.getQuestionId() + " 不属于本次作答");
            }
            answer.setAnswer(normalizeAnswer(answer.getQuestionType(), item.getAnswer()));
            attemptRepository.saveAnswer(answer);
            saved++;
        }
        applySwitchCount(attempt, command.getSwitchCount());
        return saved;
    }

    @Override
    @Transactional
    public AttemptResultView submit(Long attemptId, LmsActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        ExamAttempt attempt = requireOwnedAttempt(attemptId, userId);
        attempt.assertInProgress();
        Exam exam = requireExam(attempt.getExamId());
        attempt.submit(LocalDateTime.now());
        gradeObjective(attempt);
        List<ExamAnswer> answers = attemptRepository.listAnswers(attemptId);
        boolean allGraded = !scoringSupport.hasPendingGrade(answers);
        attempt.applyScores(scoringSupport.objectiveTotal(answers), scoringSupport.subjectiveTotal(answers),
                scoringSupport.passScoreOf(exam), allGraded);
        attemptRepository.save(attempt);
        ExamScore score = scoringSupport.writeScoreVersion(attempt, null,
                allGraded ? "交卷自动判分" : "交卷自动判分，主观题待人工判分");
        accrueIfPassed(score, attempt);
        return buildResult(attempt, exam);
    }

    @Override
    public ExamMetricsView metrics() {
        assertGate();
        ExamMetricsView view = new ExamMetricsView();
        view.setTakerTotal(attemptRepository.countDistinctTakers());
        view.setPassedTotal(attemptRepository.countDistinctPassed());
        return view;
    }

    /**
     * 成绩及格时自动记学分。
     *
     * <p>按<b>考试</b>记账而不是按作答记录：补考通过仍是同一门考试，
     * 按作答记录记会让补考的人拿两份学分（台账唯一键挡的是同来源同 ID，
     * 而补考的 attemptId 是新值，挡不住）。</p>
     *
     * <p>失败只记日志：学分是附赠，不能让「没配规则」把已经交上去的卷子搞失败。</p>
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

    @Override
    public PageResult<AttemptView> mine(AttemptQuery query, LmsActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        query.setUserId(userId);
        long current = Math.max(query.getCurrent(), 1L);
        long size = Math.min(Math.max(query.getSize(), 1L), properties.getAttemptPageLimit());
        long total = attemptRepository.count(query);
        List<AttemptView> views = attemptRepository.page(query, (current - 1) * size, (int) size)
                .stream().map(AttemptView::from).toList();
        for (AttemptView view : views) {
            examRepository.findById(view.getExamId()).ifPresent(exam -> view.setExamTitle(exam.getTitle()));
        }
        return PageResult.of(views, total, current, size);
    }

    @Override
    public AttemptResultView result(Long attemptId, LmsActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        ExamAttempt attempt = requireOwnedAttempt(attemptId, userId);
        Exam exam = examRepository.findById(attempt.getExamId()).orElse(null);
        return buildResult(attempt, exam);
    }

    @Override
    public PageResult<ScoreView> myScores(long current, long size, LmsActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        long page = Math.max(current, 1L);
        long limit = Math.min(Math.max(size, 1L), properties.getAttemptPageLimit());
        long total = scoreRepository.countByUser(userId);
        List<ScoreView> views = scoreRepository.pageByUser(userId, (page - 1) * limit, (int) limit)
                .stream().map(ScoreView::from).toList();
        for (ScoreView view : views) {
            examRepository.findById(view.getExamId()).ifPresent(exam -> view.setExamTitle(exam.getTitle()));
        }
        return PageResult.of(views, total, page, limit);
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
     * 加载考试，不存在按 404 处理。
     *
     * @param id 考试 ID
     * @return 考试
     */
    private Exam requireExam(Long id) {
        return examRepository.findById(id)
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.EXAM_NOT_FOUND));
    }

    /**
     * 加载作答记录并校验归属（他人记录一律按不存在处理，避免探测）。
     *
     * @param attemptId 作答记录 ID
     * @param userId 当前学员用户 ID
     * @return 作答记录
     */
    private ExamAttempt requireOwnedAttempt(Long attemptId, Long userId) {
        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.ATTEMPT_NOT_FOUND));
        if (!userId.equals(attempt.getUserId())) {
            throw LmsErrorCode.exception(LmsErrorCode.ATTEMPT_NOT_FOUND);
        }
        return attempt;
    }

    /**
     * 按试卷题目生成本次作答的明细骨架（一题一条，含题序）。
     *
     * @param attempt 作答记录
     * @param exam 考试
     * @param paper 试卷
     */
    private void buildAnswerSkeletons(ExamAttempt attempt, Exam exam, Paper paper) {
        List<PaperQuestion> items = paperRepository.listQuestions(paper.getId());
        if (items.isEmpty()) {
            throw LmsErrorCode.exception(LmsErrorCode.EXAM_INVALID, "试卷内没有题目，请联系管理员检查试卷");
        }
        List<PaperQuestion> ordered = new ArrayList<>(items);
        if (exam.isShuffle()) {
            ordered.sort(Comparator
                    .comparingLong((PaperQuestion item) -> PickOrder.keyOf(attempt.getSeed(), item.getQuestionId()))
                    .thenComparing(PaperQuestion::getQuestionId));
        } else {
            ordered.sort(Comparator
                    .comparing((PaperQuestion item) -> item.getSortNo() == null ? Integer.MAX_VALUE : item.getSortNo())
                    .thenComparing(PaperQuestion::getQuestionId));
        }
        Map<Long, Question> questions = scoringSupport.loadQuestions(
                ordered.stream().map(PaperQuestion::getQuestionId).toList());
        int sortNo = 1;
        for (PaperQuestion item : ordered) {
            Question question = questions.get(item.getQuestionId());
            if (question == null) {
                throw LmsErrorCode.exception(LmsErrorCode.EXAM_INVALID,
                        "试卷中的题目 " + item.getQuestionId() + " 已不存在，请联系管理员检查试卷");
            }
            ExamAnswer answer = new ExamAnswer();
            answer.setAttemptId(attempt.getId());
            answer.setQuestionId(item.getQuestionId());
            answer.setQuestionType(question.getType());
            answer.setGraded(false);
            answer.setSortNo(sortNo);
            attemptRepository.saveAnswer(answer);
            sortNo++;
        }
    }

    /**
     * 自动判客观题（主观题保持待判状态）。
     *
     * @param attempt 作答记录
     */
    private void gradeObjective(ExamAttempt attempt) {
        List<ExamAnswer> answers = attemptRepository.listAnswers(attempt.getId());
        Map<Long, BigDecimal> maxScores = scoringSupport.paperScoreMap(attempt.getPaperId());
        Map<Long, Question> questions = scoringSupport.loadQuestions(
                answers.stream().map(ExamAnswer::getQuestionId).toList());
        for (ExamAnswer answer : answers) {
            if (answer.isGraded() || answer.getQuestionType() == null
                    || !answer.getQuestionType().isObjective()) {
                continue;
            }
            Question question = questions.get(answer.getQuestionId());
            boolean correct = question != null && Judge.isCorrect(answer.getQuestionType(),
                    question.getStandardAnswer(), answer.getAnswer());
            BigDecimal max = maxScores.getOrDefault(answer.getQuestionId(), BigDecimal.ZERO);
            answer.setCorrect(correct);
            answer.setScore(correct ? max : BigDecimal.ZERO);
            answer.setGraded(true);
            attemptRepository.saveAnswer(answer);
        }
    }

    /**
     * 归一学员作答，非法答案转成明确的作答错误。
     *
     * @param type 题型
     * @param raw 原始作答
     * @return 归一后的作答
     */
    private String normalizeAnswer(QuestionType type, String raw) {
        try {
            return Question.normalizeAnswer(type, raw);
        } catch (BizException ex) {
            throw LmsErrorCode.exception(LmsErrorCode.ANSWER_INVALID, ex.getMessage());
        }
    }

    /**
     * 回写切屏次数（只增不减，避免前端刷新把计数清零）。
     *
     * @param attempt 作答记录
     * @param switchCount 前端上报的切屏次数
     */
    private void applySwitchCount(ExamAttempt attempt, Integer switchCount) {
        if (switchCount == null) {
            return;
        }
        int current = attempt.getSwitchCount() == null ? 0 : attempt.getSwitchCount();
        if (switchCount > current) {
            attempt.setSwitchCount(switchCount);
            attemptRepository.save(attempt);
        }
    }

    /**
     * 计算剩余作答秒数（已交卷返回 0）。
     *
     * @param attempt 作答记录
     * @return 剩余秒数
     */
    private long remainingSeconds(ExamAttempt attempt) {
        if (attempt.getStatus() == null || !attempt.getStatus().isOpen() || attempt.getDeadlineAt() == null) {
            return 0L;
        }
        long seconds = Duration.between(LocalDateTime.now(), attempt.getDeadlineAt()).getSeconds();
        return Math.max(seconds, 0L);
    }

    /**
     * 组装卷面单题视图。
     *
     * @param answer 作答明细
     * @param question 题目
     * @param maxScore 本题满分
     * @return 单题视图
     */
    private AttemptQuestionView buildQuestionView(ExamAnswer answer, Question question, BigDecimal maxScore) {
        AttemptQuestionView view = new AttemptQuestionView();
        view.setQuestionId(answer.getQuestionId());
        view.setType(answer.getQuestionType() == null ? null : answer.getQuestionType().name());
        view.setScore(maxScore);
        view.setSortNo(answer.getSortNo());
        view.setAnswer(answer.getAnswer());
        if (question != null) {
            view.setStem(question.getStem());
            view.getOptions().addAll(question.getOptions().stream().map(option -> {
                AttemptQuestionView.OptionView optionView = new AttemptQuestionView.OptionView();
                optionView.setOptionKey(option.getOptionKey());
                optionView.setContent(option.getContent());
                return optionView;
            }).toList());
        }
        return view;
    }

    /**
     * 组装交卷结果视图。
     *
     * @param attempt 作答记录
     * @param exam 考试（可为空）
     * @return 结果视图
     */
    private AttemptResultView buildResult(ExamAttempt attempt, Exam exam) {
        List<ExamAnswer> answers = attemptRepository.listAnswers(attempt.getId());
        Map<Long, Question> questions = scoringSupport.loadQuestions(
                answers.stream().map(ExamAnswer::getQuestionId).toList());
        Map<Long, BigDecimal> maxScores = scoringSupport.paperScoreMap(attempt.getPaperId());
        AttemptResultView view = new AttemptResultView();
        view.setAttemptId(attempt.getId());
        view.setExamId(attempt.getExamId());
        view.setExamTitle(exam == null ? null : exam.getTitle());
        view.setAttemptNo(attempt.getAttemptNo());
        view.setStatus(attempt.getStatus() == null ? null : attempt.getStatus().name());
        view.setObjectiveScore(attempt.getObjectiveScore());
        view.setSubjectiveScore(attempt.getSubjectiveScore());
        view.setTotalScore(attempt.getTotalScore());
        view.setPassScore(exam == null ? null : scoringSupport.passScoreOf(exam));
        view.setPassed(attempt.getPassed());
        view.setSubmittedAt(attempt.getSubmittedAt());
        view.setPendingGrade(scoringSupport.hasPendingGrade(answers));
        scoreRepository.findLatest(attempt.getId()).ifPresent(score -> view.setScoreVersion(score.getVersionNo()));
        List<ResultQuestionView> items = new ArrayList<>();
        for (ExamAnswer answer : answers) {
            ResultQuestionView item = new ResultQuestionView();
            item.setQuestionId(answer.getQuestionId());
            item.setType(answer.getQuestionType() == null ? null : answer.getQuestionType().name());
            item.setMaxScore(maxScores.getOrDefault(answer.getQuestionId(), BigDecimal.ZERO));
            item.setScore(answer.getScore());
            item.setAnswer(answer.getAnswer());
            item.setCorrect(answer.getCorrect());
            item.setGraded(answer.isGraded());
            Question question = questions.get(answer.getQuestionId());
            if (question != null) {
                item.setStem(question.getStem());
            }
            items.add(item);
        }
        view.setQuestions(items);
        return view;
    }
}
