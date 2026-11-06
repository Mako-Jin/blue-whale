package com.bluewhale.lms.exam.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.lms.common.LmsErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 题库领域规则：答案归一、选项与答案一致性、启用前完整性校验。
 *
 * @author BlueWhale
 */
class QuestionDomainRuleTest {

    @Test
    @DisplayName("单选题答案转大写，选项正确标记由答案派生")
    void singleChoiceShouldNormalizeAnswerAndSyncOptions() {
        Question question = question(QuestionType.SINGLE, "b",
                option("A", "选项甲"), option("B", "选项乙"));
        question.validateForActivate();
        assertEquals("B", question.getStandardAnswer());
        assertFalse(question.getOptions().get(0).isCorrect());
        assertTrue(question.getOptions().get(1).isCorrect());
    }

    @Test
    @DisplayName("多选题答案去重并升序归一")
    void multipleChoiceShouldSortAndDedupeAnswer() {
        Question question = question(QuestionType.MULTIPLE, " c, a ,c ",
                option("A", "甲"), option("B", "乙"), option("C", "丙"));
        question.validateForActivate();
        assertEquals("A,C", question.getStandardAnswer());
        assertTrue(question.getOptions().get(0).isCorrect());
        assertFalse(question.getOptions().get(1).isCorrect());
        assertTrue(question.getOptions().get(2).isCorrect());
    }

    @Test
    @DisplayName("判断题中文答案归一为 TRUE / FALSE")
    void judgeQuestionShouldNormalizeChineseAnswer() {
        Question question = question(QuestionType.JUDGE, "对");
        question.validateForActivate();
        assertEquals("TRUE", question.getStandardAnswer());
        Question wrong = question(QuestionType.JUDGE, "错误");
        wrong.validateForActivate();
        assertEquals("FALSE", wrong.getStandardAnswer());
    }

    @Test
    @DisplayName("答案键不在选项内时报错")
    void answerOutsideOptionsShouldFail() {
        Question question = question(QuestionType.SINGLE, "D",
                option("A", "甲"), option("B", "乙"));
        BizException exception = assertThrows(BizException.class, question::validateForActivate);
        assertEquals(LmsErrorCode.QUESTION_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("判断题配置选项时报错")
    void judgeQuestionShouldRejectOptions() {
        Question question = question(QuestionType.JUDGE, "TRUE", option("A", "甲"));
        BizException exception = assertThrows(BizException.class, question::validateForActivate);
        assertEquals(LmsErrorCode.QUESTION_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("启用时缺少答案报错，草稿仅校验基础字段")
    void activateShouldRequireAnswer() {
        Question draft = question(QuestionType.SINGLE, null, option("A", "甲"), option("B", "乙"));
        draft.validateBasics();
        BizException exception = assertThrows(BizException.class, draft::validateForActivate);
        assertEquals(LmsErrorCode.QUESTION_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("选择题选项少于两个时报错")
    void choiceQuestionShouldRequireTwoOptions() {
        Question question = question(QuestionType.SINGLE, "A", option("A", "甲"));
        BizException exception = assertThrows(BizException.class, question::validateForActivate);
        assertEquals(LmsErrorCode.QUESTION_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("题目编码规范化为大写并拒绝空值")
    void codeShouldBeNormalized() {
        assertEquals("Q-001", Question.normalizeCode("  q-001 "));
        assertThrows(BizException.class, () -> Question.normalizeCode("  "));
    }

    /**
     * 构造题目。
     *
     * @param type 题型
     * @param answer 标准答案
     * @param options 选项
     * @return 题目
     */
    private Question question(QuestionType type, String answer, QuestionOption... options) {
        Question question = new Question();
        question.setQuestionCode("Q-001");
        question.setCategory("安全生产");
        question.setType(type);
        question.setDifficulty(Difficulty.NORMAL);
        question.setStem("示例题干");
        question.setStandardAnswer(answer);
        question.setDefaultScore(new BigDecimal("2.0"));
        question.setStatus(QuestionStatus.DRAFT);
        question.setOptions(new ArrayList<>(List.of(options)));
        return question;
    }

    /**
     * 构造选项。
     *
     * @param key 选项键
     * @param content 内容
     * @return 选项
     */
    private QuestionOption option(String key, String content) {
        QuestionOption option = new QuestionOption();
        option.setOptionKey(key);
        option.setContent(content);
        return option;
    }
}
