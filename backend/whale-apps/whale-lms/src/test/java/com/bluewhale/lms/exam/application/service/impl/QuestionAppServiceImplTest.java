package com.bluewhale.lms.exam.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.exam.application.command.QuestionOptionCommand;
import com.bluewhale.lms.exam.application.command.QuestionSaveCommand;
import com.bluewhale.lms.exam.application.command.QuestionStatusCommand;
import com.bluewhale.lms.exam.domain.model.Question;
import com.bluewhale.lms.exam.domain.model.QuestionStatus;
import com.bluewhale.lms.exam.domain.model.QuestionType;
import com.bluewhale.lms.exam.domain.repository.PaperRepository;
import com.bluewhale.lms.exam.domain.repository.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 题库应用服务：编码冲突、草稿宽松、启用严格、删除引用校验。
 *
 * @author BlueWhale
 */
class QuestionAppServiceImplTest {

    private QuestionRepository questionRepository;

    private PaperRepository paperRepository;

    private QuestionAppServiceImpl service;

    private final LmsActor admin = new LmsActor(7L, "lmsadmin", Set.of("lms_admin"), Set.of());

    @BeforeEach
    void setUp() {
        questionRepository = mock(QuestionRepository.class);
        paperRepository = mock(PaperRepository.class);
        service = new QuestionAppServiceImpl(questionRepository, paperRepository, new LmsProperties());
    }

    @Test
    @DisplayName("题目编码重复时拒绝新建")
    void createShouldRejectDuplicateCode() {
        when(questionRepository.findByCode("Q-001")).thenReturn(Optional.of(new Question()));
        BizException exception = assertThrows(BizException.class,
                () -> service.create(command("Q-001", null, List.of(option("A"), option("B"))), admin));
        assertEquals(LmsErrorCode.QUESTION_CODE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("草稿允许缺答案，仅校验基础字段")
    void createShouldAllowDraftWithoutAnswer() {
        when(questionRepository.findByCode("Q-002")).thenReturn(Optional.empty());
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> {
            Question saved = invocation.getArgument(0);
            saved.setId(10L);
            return 10L;
        });
        Long id = service.create(command("Q-002", null, List.of(option("A"), option("B"))), admin);
        assertEquals(10L, id);
        verify(questionRepository).save(any(Question.class));
    }

    @Test
    @DisplayName("直接启用但缺答案时报错")
    void createShouldFailWhenActivatedWithoutAnswer() {
        when(questionRepository.findByCode("Q-003")).thenReturn(Optional.empty());
        QuestionSaveCommand command = command("Q-003", null, List.of(option("A"), option("B")));
        command.setActivate(true);
        BizException exception = assertThrows(BizException.class, () -> service.create(command, admin));
        assertEquals(LmsErrorCode.QUESTION_INVALID, exception.getCode());
        verify(questionRepository, never()).save(any(Question.class));
    }

    @Test
    @DisplayName("启用动作对不完整题目失败")
    void changeStatusShouldValidateBeforeActivate() {
        Question draft = new Question();
        draft.setId(10L);
        draft.setStatus(QuestionStatus.DRAFT);
        draft.setType(QuestionType.SINGLE);
        draft.setStem("题干");
        draft.setCategory("分类");
        draft.setDefaultScore(BigDecimal.ONE);
        when(questionRepository.findById(10L)).thenReturn(Optional.of(draft));
        QuestionStatusCommand command = new QuestionStatusCommand();
        command.setAction("ACTIVATE");
        BizException exception = assertThrows(BizException.class,
                () -> service.changeStatus(10L, command, admin));
        assertEquals(LmsErrorCode.QUESTION_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("题目被试卷引用时拒绝删除")
    void deleteShouldRejectReferencedQuestion() {
        Question exist = new Question();
        exist.setId(10L);
        when(questionRepository.findById(10L)).thenReturn(Optional.of(exist));
        when(paperRepository.countByQuestionId(10L)).thenReturn(2L);
        BizException exception = assertThrows(BizException.class, () -> service.delete(10L, admin));
        assertEquals(LmsErrorCode.QUESTION_STATE_CONFLICT, exception.getCode());
        assertTrue(exception.getMessage().contains("2 份试卷"));
        verify(questionRepository, never()).deleteById(10L);
    }

    /**
     * 构造保存命令。
     *
     * @param code 编码
     * @param answer 答案
     * @param options 选项
     * @return 命令
     */
    private QuestionSaveCommand command(String code, String answer, List<QuestionOptionCommand> options) {
        QuestionSaveCommand command = new QuestionSaveCommand();
        command.setQuestionCode(code);
        command.setCategory("安全生产");
        command.setType("SINGLE");
        command.setDifficulty("NORMAL");
        command.setStem("示例题干");
        command.setStandardAnswer(answer);
        command.setDefaultScore(new BigDecimal("2.0"));
        command.setOptions(options);
        return command;
    }

    /**
     * 构造选项命令。
     *
     * @param key 选项键
     * @return 选项命令
     */
    private QuestionOptionCommand option(String key) {
        QuestionOptionCommand command = new QuestionOptionCommand();
        command.setOptionKey(key);
        command.setContent("选项" + key);
        return command;
    }
}
