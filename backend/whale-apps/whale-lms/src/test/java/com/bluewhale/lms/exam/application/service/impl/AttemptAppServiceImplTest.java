package com.bluewhale.lms.exam.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.credit.application.service.CreditAppService;
import com.bluewhale.lms.exam.application.command.AttemptAnswerCommand;
import com.bluewhale.lms.exam.application.command.AttemptSaveCommand;
import com.bluewhale.lms.exam.application.support.ExamScoringSupport;
import com.bluewhale.lms.exam.domain.model.AttemptStatus;
import com.bluewhale.lms.exam.domain.model.Exam;
import com.bluewhale.lms.exam.domain.model.ExamAnswer;
import com.bluewhale.lms.exam.domain.model.ExamAttempt;
import com.bluewhale.lms.exam.domain.model.ExamScore;
import com.bluewhale.lms.exam.domain.model.ExamStatus;
import com.bluewhale.lms.exam.domain.model.Paper;
import com.bluewhale.lms.exam.domain.model.PaperQuestion;
import com.bluewhale.lms.exam.domain.model.PaperStatus;
import com.bluewhale.lms.exam.domain.model.Question;
import com.bluewhale.lms.exam.domain.model.QuestionStatus;
import com.bluewhale.lms.exam.domain.model.QuestionType;
import com.bluewhale.lms.exam.domain.repository.AttemptRepository;
import com.bluewhale.lms.exam.domain.repository.ExamRepository;
import com.bluewhale.lms.exam.domain.repository.PaperRepository;
import com.bluewhale.lms.exam.domain.repository.QuestionRepository;
import com.bluewhale.lms.exam.domain.repository.ScoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 作答应用服务：作答次数、题序可复现、答案归一、交卷判分与成绩版本。
 *
 * @author BlueWhale
 */
class AttemptAppServiceImplTest {

    /** 考试 ID */
    private static final long EXAM_ID = 21L;

    /** 试卷 ID */
    private static final long PAPER_ID = 11L;

    /** 单题 ID（单选） */
    private static final long SINGLE_ID = 301L;

    /** 简答题 ID */
    private static final long SHORT_ID = 302L;

    private ExamRepository examRepository;

    private PaperRepository paperRepository;

    private QuestionRepository questionRepository;

    private AttemptRepository attemptRepository;

    private ScoreRepository scoreRepository;

    private AttemptAppServiceImpl service;

    private List<ExamAnswer> answerStore;

    private List<ExamScore> scoreStore;

    private ExamAttempt created;

    private long answerSeq;

    private final LmsActor learner = new LmsActor(7L, "learner", Set.of("lms_member"), Set.of());

    @BeforeEach
    void setUp() {
        examRepository = mock(ExamRepository.class);
        paperRepository = mock(PaperRepository.class);
        questionRepository = mock(QuestionRepository.class);
        attemptRepository = mock(AttemptRepository.class);
        scoreRepository = mock(ScoreRepository.class);
        answerStore = new ArrayList<>();
        scoreStore = new ArrayList<>();
        answerSeq = 1000L;
        stubAttemptRepository();
        stubScoreRepository();
        service = new AttemptAppServiceImpl(examRepository, paperRepository, attemptRepository, scoreRepository,
                new ExamScoringSupport(paperRepository, questionRepository, scoreRepository),
                mock(CreditAppService.class), new LmsProperties());
    }

    @Test
    @DisplayName("存在未交卷记录时直接复用，不再新建")
    void startShouldReuseInProgress() {
        ExamAttempt existing = new ExamAttempt();
        existing.setId(55L);
        existing.setExamId(EXAM_ID);
        existing.setUserId(7L);
        existing.setStatus(AttemptStatus.IN_PROGRESS);
        when(examRepository.findById(EXAM_ID)).thenReturn(Optional.of(openExam()));
        when(paperRepository.findById(PAPER_ID)).thenReturn(Optional.of(publishedPaper()));
        when(attemptRepository.findInProgress(EXAM_ID, 7L)).thenReturn(Optional.of(existing));

        assertEquals(55L, service.start(EXAM_ID, learner));
        verify(attemptRepository, never()).save(any());
    }

    @Test
    @DisplayName("作答次数用尽后拒绝开始")
    void startShouldRejectWhenAttemptsExhausted() {
        Exam exam = openExam();
        exam.setMaxAttempts(1);
        when(examRepository.findById(EXAM_ID)).thenReturn(Optional.of(exam));
        when(paperRepository.findById(PAPER_ID)).thenReturn(Optional.of(publishedPaper()));
        when(attemptRepository.countByExamAndUser(EXAM_ID, 7L)).thenReturn(1L);

        BizException exception = assertThrows(BizException.class, () -> service.start(EXAM_ID, learner));
        assertEquals(LmsErrorCode.EXAM_ATTEMPT_LIMIT, exception.getCode());
    }

    @Test
    @DisplayName("不在考试时间窗内拒绝开始")
    void startShouldRejectOutsideWindow() {
        Exam exam = openExam();
        exam.setStartTime(LocalDateTime.now().plusHours(1));
        when(examRepository.findById(EXAM_ID)).thenReturn(Optional.of(exam));

        BizException exception = assertThrows(BizException.class, () -> service.start(EXAM_ID, learner));
        assertEquals(LmsErrorCode.EXAM_WINDOW_CLOSED, exception.getCode());
    }

    @Test
    @DisplayName("同一份作答重复打开得到同一题序")
    void startShouldProduceStableOrder() {
        stubExamAndPaper(true);
        List<Long> first = runStart();
        answerStore.clear();
        created = null;
        List<Long> second = runStart();
        assertEquals(first, second);
        assertEquals(2, first.size());
    }

    @Test
    @DisplayName("乱序只改呈现次序，判分仍按题目 ID 命中")
    void submitShouldGradeByQuestionIdRegardlessOfOrder() {
        stubExamAndPaper(true);
        runStart();
        service.save(100L, saveCommand(SINGLE_ID, "a"), learner);

        ExamAttempt attempt = created;
        assertTrue(answerStore.stream().anyMatch(item -> item.getQuestionType() == QuestionType.SHORT_ANSWER));

        service.submit(100L, learner);

        ExamAnswer single = answerOf(SINGLE_ID);
        assertEquals("A", single.getAnswer());
        assertTrue(single.isGraded());
        assertTrue(single.getCorrect());
        assertEquals(0, new BigDecimal("5").compareTo(single.getScore()));
        assertFalse(answerOf(SHORT_ID).isGraded());
        assertEquals(AttemptStatus.SUBMITTED, attempt.getStatus());
        assertEquals(0, new BigDecimal("5").compareTo(attempt.getObjectiveScore()));
        assertTrue(attempt.getPassed());
        assertEquals(1, scoreStore.size());
        assertEquals(1, scoreStore.get(0).getVersionNo());
    }

    @Test
    @DisplayName("客观题答错得 0 分且标记为错")
    void submitShouldScoreZeroForWrongAnswer() {
        stubExamAndPaper(false);
        runStart();
        service.save(100L, saveCommand(SINGLE_ID, "B"), learner);
        service.submit(100L, learner);

        ExamAnswer single = answerOf(SINGLE_ID);
        assertFalse(single.getCorrect());
        assertEquals(0, BigDecimal.ZERO.compareTo(single.getScore()));
    }

    @Test
    @DisplayName("超过本次截止时间不可保存答案")
    void saveShouldRejectAfterDeadline() {
        stubExamAndPaper(false);
        runStart();
        created.setDeadlineAt(LocalDateTime.now().minusMinutes(1));

        BizException exception = assertThrows(BizException.class,
                () -> service.save(100L, saveCommand(SINGLE_ID, "A"), learner));
        assertEquals(LmsErrorCode.ATTEMPT_EXPIRED, exception.getCode());
    }

    @Test
    @DisplayName("提交不属于本次作答的题目整批失败")
    void saveShouldRejectUnknownQuestion() {
        stubExamAndPaper(false);
        runStart();

        BizException exception = assertThrows(BizException.class,
                () -> service.save(100L, saveCommand(999L, "A"), learner));
        assertEquals(LmsErrorCode.ANSWER_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("已交卷后不可再保存答案")
    void saveShouldRejectAfterSubmit() {
        stubExamAndPaper(false);
        runStart();
        service.submit(100L, learner);

        BizException exception = assertThrows(BizException.class,
                () -> service.save(100L, saveCommand(SINGLE_ID, "A"), learner));
        assertEquals(LmsErrorCode.ATTEMPT_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("他人作答按不存在处理")
    void shouldHideOthersAttempt() {
        stubExamAndPaper(false);
        runStart();
        LmsActor other = new LmsActor(8L, "other", Set.of("lms_member"), Set.of());

        BizException exception = assertThrows(BizException.class,
                () -> service.save(100L, saveCommand(SINGLE_ID, "A"), other));
        assertEquals(LmsErrorCode.ATTEMPT_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("切屏次数只增不减")
    void saveShouldKeepSwitchCountMonotonic() {
        stubExamAndPaper(false);
        runStart();
        AttemptSaveCommand command = saveCommand(SINGLE_ID, "A");
        command.setSwitchCount(3);
        service.save(100L, command, learner);
        command.setSwitchCount(1);
        service.save(100L, command, learner);
        assertEquals(3, created.getSwitchCount());
        verify(attemptRepository, times(2)).save(any());
    }

    /**
     * 运行一次开始作答并返回题序。
     *
     * @return 按呈现次序排列的题目 ID
     */
    private List<Long> runStart() {
        service.start(EXAM_ID, learner);
        return answerStore.stream()
                .sorted((left, right) -> left.getSortNo() - right.getSortNo())
                .map(ExamAnswer::getQuestionId)
                .collect(Collectors.toList());
    }

    /**
     * 取某题作答明细。
     *
     * @param questionId 题目 ID
     * @return 作答明细
     */
    private ExamAnswer answerOf(Long questionId) {
        return answerStore.stream()
                .filter(item -> item.getQuestionId().equals(questionId))
                .findFirst()
                .orElseThrow();
    }

    /**
     * 组装单题保存命令。
     *
     * @param questionId 题目 ID
     * @param answer 作答
     * @return 保存命令
     */
    private AttemptSaveCommand saveCommand(Long questionId, String answer) {
        AttemptAnswerCommand item = new AttemptAnswerCommand();
        item.setQuestionId(questionId);
        item.setAnswer(answer);
        AttemptSaveCommand command = new AttemptSaveCommand();
        command.setAnswers(List.of(item));
        return command;
    }

    /**
     * 桩化作答仓储（内存态作答明细）。
     */
    private void stubAttemptRepository() {
        when(attemptRepository.save(any())).thenAnswer(invocation -> {
            ExamAttempt attempt = invocation.getArgument(0);
            if (attempt.getId() == null) {
                attempt.setId(100L);
            }
            created = attempt;
            return attempt.getId();
        });
        when(attemptRepository.findById(anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(created)
                        .filter(attempt -> attempt.getId().equals(invocation.getArgument(0))));
        when(attemptRepository.saveAnswer(any())).thenAnswer(invocation -> {
            ExamAnswer answer = invocation.getArgument(0);
            if (answer.getId() == null) {
                answer.setId(++answerSeq);
                answerStore.add(answer);
            }
            return answer.getId();
        });
        when(attemptRepository.listAnswers(anyLong())).thenAnswer(invocation -> new ArrayList<>(answerStore));
        when(attemptRepository.findAnswer(anyLong(), anyLong())).thenAnswer(invocation -> answerStore.stream()
                .filter(item -> item.getQuestionId().equals(invocation.getArgument(1)))
                .findFirst());
    }

    /**
     * 桩化成绩仓储（内存态成绩版本）。
     */
    private void stubScoreRepository() {
        when(scoreRepository.save(any())).thenAnswer(invocation -> {
            ExamScore score = invocation.getArgument(0);
            score.setId((long) (scoreStore.size() + 1));
            scoreStore.add(score);
            return score.getId();
        });
        when(scoreRepository.listByAttempt(anyLong())).thenAnswer(invocation -> new ArrayList<>(scoreStore));
        when(scoreRepository.findLatest(anyLong())).thenAnswer(invocation -> scoreStore.stream().reduce((a, b) -> b));
    }

    /**
     * 桩化考试与试卷（含题目、分值）。
     *
     * @param shuffle 是否乱序
     */
    private void stubExamAndPaper(boolean shuffle) {
        Exam exam = openExam();
        exam.setShuffle(shuffle);
        when(examRepository.findById(EXAM_ID)).thenReturn(Optional.of(exam));
        Paper paper = publishedPaper();
        when(paperRepository.findById(PAPER_ID)).thenReturn(Optional.of(paper));
        when(paperRepository.listQuestions(PAPER_ID)).thenReturn(List.of(
                paperQuestion(SINGLE_ID, "5", 1), paperQuestion(SHORT_ID, "10", 2)));
        Map<Long, Question> questions = new HashMap<>();
        questions.put(SINGLE_ID, question(SINGLE_ID, QuestionType.SINGLE, "A", "5"));
        questions.put(SHORT_ID, question(SHORT_ID, QuestionType.SHORT_ANSWER, null, "10"));
        when(questionRepository.findById(anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(questions.get(invocation.getArgument(0))));
    }

    /**
     * 构造已发布考试（窗口覆盖当前时刻）。
     *
     * @return 考试
     */
    private Exam openExam() {
        Exam exam = new Exam();
        exam.setId(EXAM_ID);
        exam.setExamCode("EXAM-01");
        exam.setTitle("安全生产考试");
        exam.setPaperId(PAPER_ID);
        exam.setStartTime(LocalDateTime.now().minusHours(1));
        exam.setEndTime(LocalDateTime.now().plusHours(1));
        exam.setDurationMinutes(60);
        exam.setMaxAttempts(2);
        exam.setShuffle(false);
        exam.setPassScore(new BigDecimal("4"));
        exam.setSwitchLimit(3);
        exam.setStatus(ExamStatus.PUBLISHED);
        return exam;
    }

    /**
     * 构造已发布试卷。
     *
     * @return 试卷
     */
    private Paper publishedPaper() {
        Paper paper = new Paper();
        paper.setId(PAPER_ID);
        paper.setPaperCode("PAPER-01");
        paper.setTitle("安全生产试卷");
        paper.setStatus(PaperStatus.PUBLISHED);
        paper.setTotalScore(new BigDecimal("15"));
        paper.setQuestionCount(2);
        return paper;
    }

    /**
     * 构造试卷题目。
     *
     * @param questionId 题目 ID
     * @param score 分值
     * @param sortNo 题序
     * @return 试卷题目
     */
    private PaperQuestion paperQuestion(Long questionId, String score, int sortNo) {
        PaperQuestion item = new PaperQuestion();
        item.setPaperId(PAPER_ID);
        item.setQuestionId(questionId);
        item.setScore(new BigDecimal(score));
        item.setSortNo(sortNo);
        item.setSource("MANUAL");
        return item;
    }

    /**
     * 构造题目。
     *
     * @param id 题目 ID
     * @param type 题型
     * @param standardAnswer 标准答案
     * @param score 默认分值
     * @return 题目
     */
    private Question question(Long id, QuestionType type, String standardAnswer, String score) {
        Question question = new Question();
        question.setId(id);
        question.setQuestionCode("Q-" + id);
        question.setCategory("安全");
        question.setType(type);
        question.setStem("题干 " + id);
        question.setStandardAnswer(standardAnswer);
        question.setDefaultScore(new BigDecimal(score));
        question.setStatus(QuestionStatus.ACTIVE);
        return question;
    }
}
