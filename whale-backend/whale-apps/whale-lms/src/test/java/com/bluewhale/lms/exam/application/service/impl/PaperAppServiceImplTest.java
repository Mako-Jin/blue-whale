package com.bluewhale.lms.exam.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.exam.application.command.PaperGenerateCommand;
import com.bluewhale.lms.exam.application.command.PaperRuleCommand;
import com.bluewhale.lms.exam.application.view.PaperGenerateResultView;
import com.bluewhale.lms.exam.domain.model.Paper;
import com.bluewhale.lms.exam.domain.model.PaperQuestion;
import com.bluewhale.lms.exam.domain.model.PaperStatus;
import com.bluewhale.lms.exam.domain.model.Question;
import com.bluewhale.lms.exam.domain.model.QuestionStatus;
import com.bluewhale.lms.exam.domain.model.QuestionType;
import com.bluewhale.lms.exam.domain.repository.PaperRepository;
import com.bluewhale.lms.exam.domain.repository.QuestionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 试卷应用服务：抽题可复现、跨规则去重、候选不足 fail-fast、发布校验。
 *
 * @author BlueWhale
 */
class PaperAppServiceImplTest {

    private PaperRepository paperRepository;

    private QuestionRepository questionRepository;

    private PaperAppServiceImpl service;

    private final LmsActor admin = new LmsActor(7L, "lmsadmin", Set.of("lms_admin"), Set.of());

    @BeforeEach
    void setUp() {
        paperRepository = mock(PaperRepository.class);
        questionRepository = mock(QuestionRepository.class);
        service = new PaperAppServiceImpl(paperRepository, questionRepository,
                new LmsProperties(), new ObjectMapper());
    }

    @Test
    @DisplayName("同一 seed 重复抽题得到同一题序")
    void generateShouldBeReproducible() {
        Paper paper = paper(new BigDecimal("60"));
        stubPaper(paper, 10);
        List<Long> first = runGenerate(4);
        List<Long> second = runGenerate(4);
        assertEquals(first, second);
    }

    @Test
    @DisplayName("候选不足时直接失败，不用其它题补齐")
    void generateShouldFailWhenCandidatesInsufficient() {
        Paper paper = paper(new BigDecimal("60"));
        stubPaper(paper, 1);
        BizException exception = assertThrows(BizException.class, () -> runGenerate(2));
        assertEquals(LmsErrorCode.PAPER_RULE_INSUFFICIENT, exception.getCode());
        verify(paperRepository, never()).deleteQuestionsBySource(any(), any());
    }

    @Test
    @DisplayName("多条规则之间不重复取同一题")
    void generateShouldNotDuplicateAcrossRules() {
        Paper paper = paper(new BigDecimal("60"));
        stubPaper(paper, 10);
        PaperGenerateCommand command = new PaperGenerateCommand();
        command.setRules(List.of(rule(2), rule(2)));
        clearInvocations(paperRepository);
        service.generate(100L, command, admin);
        ArgumentCaptor<PaperQuestion> captor = ArgumentCaptor.forClass(PaperQuestion.class);
        verify(paperRepository, times(4)).saveQuestion(captor.capture());
        long distinct = captor.getAllValues().stream().map(PaperQuestion::getQuestionId).distinct().count();
        assertEquals(4L, distinct);
    }

    @Test
    @DisplayName("空卷不可发布")
    void publishShouldRejectEmptyPaper() {
        Paper paper = paper(new BigDecimal("60"));
        when(paperRepository.findById(100L)).thenReturn(Optional.of(paper));
        when(paperRepository.listQuestions(100L)).thenReturn(List.of());
        BizException exception = assertThrows(BizException.class, () -> service.publish(100L, admin));
        assertEquals(LmsErrorCode.PAPER_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("及格分超过总分时拒绝发布")
    void publishShouldRejectPassScoreOverTotal() {
        Paper paper = paper(new BigDecimal("100"));
        when(paperRepository.findById(100L)).thenReturn(Optional.of(paper));
        when(paperRepository.listQuestions(100L)).thenReturn(List.of(paperQuestion(1L, "5.0")));
        BizException exception = assertThrows(BizException.class, () -> service.publish(100L, admin));
        assertEquals(LmsErrorCode.PAPER_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("发布成功后回写总分与题目数")
    void publishShouldWriteBackTotals() {
        Paper paper = paper(new BigDecimal("6"));
        when(paperRepository.findById(100L)).thenReturn(Optional.of(paper));
        when(paperRepository.listQuestions(100L)).thenReturn(List.of(
                paperQuestion(1L, "4.0"), paperQuestion(2L, "6.0")));
        service.publish(100L, admin);
        assertEquals(PaperStatus.PUBLISHED, paper.getStatus());
        assertEquals(new BigDecimal("10.0"), paper.getTotalScore());
        assertEquals(2, paper.getQuestionCount());
        verify(paperRepository).save(paper);
    }

    /**
     * 执行一次抽题并返回抽到的题目 ID 顺序。
     *
     * @param count 每规则抽取数量
     * @return 题目 ID 顺序
     */
    private List<Long> runGenerate(int count) {
        PaperGenerateCommand command = new PaperGenerateCommand();
        command.setRules(List.of(rule(count)));
        clearInvocations(paperRepository);
        PaperGenerateResultView view = service.generate(100L, command, admin);
        ArgumentCaptor<PaperQuestion> captor = ArgumentCaptor.forClass(PaperQuestion.class);
        verify(paperRepository, times(count)).saveQuestion(captor.capture());
        assertEquals(count, view.getPicked());
        return captor.getAllValues().stream().map(PaperQuestion::getQuestionId).toList();
    }

    /**
     * 打桩：试卷存在、当前无题目、题库有指定数量候选。
     *
     * @param paper 试卷
     * @param candidateCount 候选题目数量
     */
    private void stubPaper(Paper paper, int candidateCount) {
        when(paperRepository.findById(100L)).thenReturn(Optional.of(paper));
        when(paperRepository.listQuestions(100L)).thenReturn(List.of());
        when(questionRepository.listPickCandidates(any(), any(), any())).thenReturn(candidates(candidateCount));
    }

    /**
     * 构造候选题目。
     *
     * @param count 数量
     * @return 题目列表
     */
    private List<Question> candidates(int count) {
        List<Question> questions = new ArrayList<>();
        for (long id = 1; id <= count; id++) {
            Question question = new Question();
            question.setId(id);
            question.setQuestionCode("Q-" + id);
            question.setType(QuestionType.SINGLE);
            question.setStatus(QuestionStatus.ACTIVE);
            question.setDefaultScore(new BigDecimal("2.0"));
            questions.add(question);
        }
        return questions;
    }

    /**
     * 构造抽题规则。
     *
     * @param count 抽取数量
     * @return 规则
     */
    private PaperRuleCommand rule(int count) {
        PaperRuleCommand rule = new PaperRuleCommand();
        rule.setCategory("安全生产");
        rule.setCount(count);
        return rule;
    }

    /**
     * 构造试卷。
     *
     * @param passScore 及格分
     * @return 试卷
     */
    private Paper paper(BigDecimal passScore) {
        Paper paper = new Paper();
        paper.setId(100L);
        paper.setPaperCode("PAPER-001");
        paper.setTitle("安全生产考试");
        paper.setDurationMinutes(60);
        paper.setPassScore(passScore);
        paper.setStatus(PaperStatus.DRAFT);
        return paper;
    }

    /**
     * 构造试卷题目。
     *
     * @param questionId 题目 ID
     * @param score 分值
     * @return 试卷题目
     */
    private PaperQuestion paperQuestion(Long questionId, String score) {
        PaperQuestion item = new PaperQuestion();
        item.setPaperId(100L);
        item.setQuestionId(questionId);
        item.setScore(new BigDecimal(score));
        item.setSortNo(1);
        return item;
    }
}
