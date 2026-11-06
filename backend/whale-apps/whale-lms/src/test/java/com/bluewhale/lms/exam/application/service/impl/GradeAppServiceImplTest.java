package com.bluewhale.lms.exam.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.exam.application.command.GradeCommand;
import com.bluewhale.lms.exam.application.command.GradeItemCommand;
import com.bluewhale.lms.exam.application.support.ExamScoringSupport;
import com.bluewhale.lms.exam.application.view.AttemptView;
import com.bluewhale.lms.exam.application.view.GradeView;
import com.bluewhale.lms.exam.application.view.ScoreView;
import com.bluewhale.lms.exam.domain.model.AttemptQuery;
import com.bluewhale.lms.exam.domain.model.AttemptStatus;
import com.bluewhale.lms.exam.domain.model.Exam;
import com.bluewhale.lms.exam.domain.model.ExamAnswer;
import com.bluewhale.lms.exam.domain.model.ExamAttempt;
import com.bluewhale.lms.exam.domain.model.ExamScore;
import com.bluewhale.lms.exam.domain.model.ExamStatus;
import com.bluewhale.lms.exam.domain.model.PaperQuestion;
import com.bluewhale.lms.exam.domain.model.Question;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 人工判分：只判主观题、分数不得超题分、每次提交生成新成绩版本。
 *
 * @author BlueWhale
 */
class GradeAppServiceImplTest {

    /** 作答记录 ID */
    private static final long ATTEMPT_ID = 100L;

    /** 考试 ID */
    private static final long EXAM_ID = 21L;

    /** 试卷 ID */
    private static final long PAPER_ID = 11L;

    /** 单选题 ID */
    private static final long SINGLE_ID = 301L;

    /** 简答题 ID */
    private static final long SHORT_ID = 302L;

    private AttemptRepository attemptRepository;

    private ExamRepository examRepository;

    private ScoreRepository scoreRepository;

    private GradeAppServiceImpl service;

    private List<ExamAnswer> answerStore;

    private List<ExamScore> scoreStore;

    private ExamAttempt attempt;

    private final LmsActor lecturer = new LmsActor(9L, "teacher", Set.of("lms_lecturer"), Set.of());

    @BeforeEach
    void setUp() {
        attemptRepository = mock(AttemptRepository.class);
        examRepository = mock(ExamRepository.class);
        scoreRepository = mock(ScoreRepository.class);
        PaperRepository paperRepository = mock(PaperRepository.class);
        QuestionRepository questionRepository = mock(QuestionRepository.class);
        answerStore = new ArrayList<>();
        scoreStore = new ArrayList<>();
        attempt = submittedAttempt();
        answerStore.add(objectiveAnswer());
        answerStore.add(subjectiveAnswer());
        stubRepositories(paperRepository, questionRepository);
        service = new GradeAppServiceImpl(attemptRepository, examRepository,
                new ExamScoringSupport(paperRepository, questionRepository, scoreRepository), new LmsProperties());
    }

    @Test
    @DisplayName("客观题不接受人工判分")
    void gradeShouldRejectObjectiveQuestion() {
        GradeCommand command = command(SINGLE_ID, "5");
        BizException exception = assertThrows(BizException.class, () -> service.grade(ATTEMPT_ID, command, lecturer));
        assertEquals(LmsErrorCode.GRADE_NOT_PENDING, exception.getCode());
    }

    @Test
    @DisplayName("本题得分超过满分直接拒绝")
    void gradeShouldRejectScoreOverMax() {
        GradeCommand command = command(SHORT_ID, "11");
        BizException exception = assertThrows(BizException.class, () -> service.grade(ATTEMPT_ID, command, lecturer));
        assertEquals(LmsErrorCode.GRADE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("题目不在试卷内无法判分")
    void gradeShouldRejectQuestionOutsidePaper() {
        GradeCommand command = command(999L, "1");
        BizException exception = assertThrows(BizException.class, () -> service.grade(ATTEMPT_ID, command, lecturer));
        assertEquals(LmsErrorCode.ANSWER_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("未交卷的作答不可判分")
    void gradeShouldRejectInProgressAttempt() {
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        GradeCommand command = command(SHORT_ID, "8");
        BizException exception = assertThrows(BizException.class, () -> service.grade(ATTEMPT_ID, command, lecturer));
        assertEquals(LmsErrorCode.ATTEMPT_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("判完最后一题置为已判分并生成新版本成绩")
    void gradeShouldFinalizeAttemptAndVersion() {
        scoreStore.add(existingVersion());
        GradeCommand command = command(SHORT_ID, "8");
        command.setRemark("第二版：补充判分");

        ScoreView view = service.grade(ATTEMPT_ID, command, lecturer);

        assertEquals(2, view.getVersionNo());
        assertEquals(AttemptStatus.GRADED, attempt.getStatus());
        assertEquals(0, new BigDecimal("13").compareTo(attempt.getTotalScore()));
        assertTrue(attempt.getPassed());
        assertTrue(answerStore.stream().allMatch(ExamAnswer::isGraded));
        assertEquals(2, scoreStore.size());
        assertEquals("第二版：补充判分", scoreStore.get(1).getRemark());
        assertEquals(9L, scoreStore.get(1).getGradedBy());
    }

    @Test
    @DisplayName("只判一题时仍保持已交卷待判状态")
    void gradeShouldKeepSubmittedWhenPendingRemain() {
        answerStore.add(pendingSubjective(303L));
        when(attemptRepository.countPendingGrade(ATTEMPT_ID)).thenReturn(1L);

        GradeCommand command = command(SHORT_ID, "8");
        service.grade(ATTEMPT_ID, command, lecturer);

        assertEquals(AttemptStatus.SUBMITTED, attempt.getStatus());
        assertEquals(0, new BigDecimal("13").compareTo(attempt.getTotalScore()));
    }

    @Test
    @DisplayName("判分明细回传标准答案供教师比对")
    void detailShouldExposeStandardAnswer() {
        GradeView view = service.detail(ATTEMPT_ID, lecturer);
        assertEquals(2, view.getAnswers().size());
        assertEquals("A", view.getAnswers().get(0).getStandardAnswer());
        assertTrue(view.getAnswers().get(0).isObjective());
        assertFalse(view.getAnswers().get(1).isObjective());
    }

    @Test
    @DisplayName("待判队列强制按已交卷过滤并统计待判题数")
    void pendingShouldForceSubmittedStatus() {
        when(attemptRepository.page(any(), anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(attempt));
        when(attemptRepository.countPendingGrade(ATTEMPT_ID)).thenReturn(1L);
        AttemptQuery query = new AttemptQuery();

        PageResult<AttemptView> page = service.pending(query, lecturer);

        assertEquals(AttemptStatus.SUBMITTED.name(), query.getStatus());
        assertEquals(1, page.getRecords().size());
        assertEquals(1L, page.getRecords().get(0).getPendingGradeCount());
        assertEquals("安全生产考试", page.getRecords().get(0).getExamTitle());
    }

    /**
     * 桩化仓储。
     *
     * @param paperRepository 试卷仓储
     * @param questionRepository 题库仓储
     */
    private void stubRepositories(PaperRepository paperRepository, QuestionRepository questionRepository) {
        when(attemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(attemptRepository.listAnswers(ATTEMPT_ID)).thenReturn(answerStore);
        when(attemptRepository.findAnswer(anyLong(), anyLong())).thenAnswer(invocation -> answerStore.stream()
                .filter(item -> item.getQuestionId().equals(invocation.getArgument(1)))
                .findFirst());
        when(attemptRepository.saveAnswer(any())).thenAnswer(invocation -> {
            ExamAnswer answer = invocation.getArgument(0);
            if (answer.getId() == null) {
                answer.setId((long) (answerStore.size() + 1));
            }
            return answer.getId();
        });
        when(examRepository.findById(EXAM_ID)).thenReturn(Optional.of(publishedExam()));
        PaperQuestion single = paperQuestion(SINGLE_ID, "5", 1);
        PaperQuestion shortAnswer = paperQuestion(SHORT_ID, "10", 2);
        when(paperRepository.listQuestions(PAPER_ID)).thenReturn(List.of(single, shortAnswer));
        Map<Long, Question> questions = new HashMap<>();
        questions.put(SINGLE_ID, question(SINGLE_ID, QuestionType.SINGLE, "A"));
        questions.put(SHORT_ID, question(SHORT_ID, QuestionType.SHORT_ANSWER, null));
        when(questionRepository.findById(anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(questions.get(invocation.getArgument(0))));
        when(scoreRepository.listByAttempt(ATTEMPT_ID)).thenAnswer(invocation -> new ArrayList<>(scoreStore));
        when(scoreRepository.save(any())).thenAnswer(invocation -> {
            ExamScore score = invocation.getArgument(0);
            score.setId((long) (scoreStore.size() + 1));
            scoreStore.add(score);
            return score.getId();
        });
    }

    /**
     * 组装判分命令。
     *
     * @param questionId 题目 ID
     * @param score 得分
     * @return 判分命令
     */
    private GradeCommand command(Long questionId, String score) {
        GradeItemCommand item = new GradeItemCommand();
        item.setQuestionId(questionId);
        item.setScore(new BigDecimal(score));
        GradeCommand command = new GradeCommand();
        command.setItems(List.of(item));
        return command;
    }

    /**
     * 构造已判分的客观题作答。
     *
     * @return 作答明细
     */
    private ExamAnswer objectiveAnswer() {
        ExamAnswer answer = new ExamAnswer();
        answer.setId(1L);
        answer.setAttemptId(ATTEMPT_ID);
        answer.setQuestionId(SINGLE_ID);
        answer.setQuestionType(QuestionType.SINGLE);
        answer.setAnswer("A");
        answer.setCorrect(true);
        answer.setScore(new BigDecimal("5"));
        answer.setGraded(true);
        answer.setSortNo(1);
        return answer;
    }

    /**
     * 构造待判的主观题作答。
     *
     * @return 作答明细
     */
    private ExamAnswer subjectiveAnswer() {
        ExamAnswer answer = new ExamAnswer();
        answer.setId(2L);
        answer.setAttemptId(ATTEMPT_ID);
        answer.setQuestionId(SHORT_ID);
        answer.setQuestionType(QuestionType.SHORT_ANSWER);
        answer.setAnswer("我的作答");
        answer.setGraded(false);
        answer.setSortNo(2);
        return answer;
    }

    /**
     * 构造另一道待判主观题。
     *
     * @param questionId 题目 ID
     * @return 作答明细
     */
    private ExamAnswer pendingSubjective(Long questionId) {
        ExamAnswer answer = new ExamAnswer();
        answer.setId(3L);
        answer.setAttemptId(ATTEMPT_ID);
        answer.setQuestionId(questionId);
        answer.setQuestionType(QuestionType.SHORT_ANSWER);
        answer.setGraded(false);
        answer.setSortNo(3);
        return answer;
    }

    /**
     * 构造已存在的成绩版本。
     *
     * @return 成绩
     */
    private ExamScore existingVersion() {
        ExamScore score = new ExamScore();
        score.setId(1L);
        score.setAttemptId(ATTEMPT_ID);
        score.setExamId(EXAM_ID);
        score.setUserId(7L);
        score.setVersionNo(1);
        score.setObjectiveScore(new BigDecimal("5"));
        score.setSubjectiveScore(BigDecimal.ZERO);
        score.setTotalScore(new BigDecimal("5"));
        return score;
    }

    /**
     * 构造已交卷的作答记录。
     *
     * @return 作答记录
     */
    private ExamAttempt submittedAttempt() {
        ExamAttempt examAttempt = new ExamAttempt();
        examAttempt.setId(ATTEMPT_ID);
        examAttempt.setExamId(EXAM_ID);
        examAttempt.setPaperId(PAPER_ID);
        examAttempt.setUserId(7L);
        examAttempt.setUsername("learner");
        examAttempt.setAttemptNo(1);
        examAttempt.setStatus(AttemptStatus.SUBMITTED);
        examAttempt.setObjectiveScore(new BigDecimal("5"));
        examAttempt.setSubjectiveScore(BigDecimal.ZERO);
        examAttempt.setTotalScore(new BigDecimal("5"));
        examAttempt.setPassed(false);
        examAttempt.setSwitchCount(0);
        return examAttempt;
    }

    /**
     * 构造已发布考试。
     *
     * @return 考试
     */
    private Exam publishedExam() {
        Exam exam = new Exam();
        exam.setId(EXAM_ID);
        exam.setExamCode("EXAM-01");
        exam.setTitle("安全生产考试");
        exam.setPaperId(PAPER_ID);
        exam.setDurationMinutes(60);
        exam.setMaxAttempts(1);
        exam.setPassScore(new BigDecimal("10"));
        exam.setSwitchLimit(0);
        exam.setStatus(ExamStatus.PUBLISHED);
        return exam;
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
     * @return 题目
     */
    private Question question(Long id, QuestionType type, String standardAnswer) {
        Question question = new Question();
        question.setId(id);
        question.setQuestionCode("Q-" + id);
        question.setCategory("安全");
        question.setType(type);
        question.setStem("题干 " + id);
        question.setStandardAnswer(standardAnswer);
        question.setDefaultScore(BigDecimal.ONE);
        return question;
    }
}
