package com.bluewhale.lms.exam.domain;

import com.bluewhale.lms.exam.domain.model.Judge;
import com.bluewhale.lms.exam.domain.model.QuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 客观题判分器：只看题型、标准答案与学员答案，与题序、选项次序无关。
 *
 * @author BlueWhale
 */
class JudgeTest {

    @Test
    @DisplayName("单选题大小写不敏感")
    void singleShouldIgnoreCase() {
        assertTrue(Judge.isCorrect(QuestionType.SINGLE, "A", "a"));
    }

    @Test
    @DisplayName("单选题答案不同即错")
    void singleShouldRejectOtherOption() {
        assertFalse(Judge.isCorrect(QuestionType.SINGLE, "A", "B"));
    }

    @Test
    @DisplayName("多选题与作答次序无关")
    void multipleShouldIgnoreOrder() {
        assertTrue(Judge.isCorrect(QuestionType.MULTIPLE, "A,C", "C,A"));
        assertTrue(Judge.isCorrect(QuestionType.MULTIPLE, "A,C", " c ， a "));
    }

    @Test
    @DisplayName("多选题少选即错（不做半分）")
    void multipleShouldRequireAllOptions() {
        assertFalse(Judge.isCorrect(QuestionType.MULTIPLE, "A,C", "A"));
    }

    @Test
    @DisplayName("判断题按归一后的 TRUE/FALSE 比较")
    void judgeShouldNormalizeBoolean() {
        assertTrue(Judge.isCorrect(QuestionType.JUDGE, "TRUE", "对"));
        assertTrue(Judge.isCorrect(QuestionType.JUDGE, "FALSE", "f"));
        assertFalse(Judge.isCorrect(QuestionType.JUDGE, "TRUE", "错"));
    }

    @Test
    @DisplayName("空答案一律判错")
    void blankAnswerShouldBeWrong() {
        assertFalse(Judge.isCorrect(QuestionType.SINGLE, "A", null));
        assertFalse(Judge.isCorrect(QuestionType.SINGLE, "A", "   "));
    }

    @Test
    @DisplayName("主观题不参与自动判分")
    void subjectiveShouldNotBeJudged() {
        assertFalse(Judge.isCorrect(QuestionType.SHORT_ANSWER, "参考答案", "参考答案"));
    }
}
