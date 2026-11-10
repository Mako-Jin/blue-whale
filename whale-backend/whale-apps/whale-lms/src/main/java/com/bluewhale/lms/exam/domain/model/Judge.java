package com.bluewhale.lms.exam.domain.model;

/**
 * 客观题判分器。
 *
 * <p>判分只依赖题型、标准答案与学员答案三要素，与题序、选项次序无关：
 * 单选 / 判断按归一后的字符串相等，多选按归一后的升序串相等（即集合相等），
 * 简答题不在此判（进入人工判分队列）。</p>
 *
 * @author BlueWhale
 */
public final class Judge {

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private Judge() {
    }

    /**
     * 判定客观题是否正确。
     *
     * @param type 题型
     * @param standardAnswer 标准答案（已归一）
     * @param answer 学员答案（原样）
     * @return 空答案与主观题一律返回 false
     */
    public static boolean isCorrect(QuestionType type, String standardAnswer, String answer) {
        if (type == null || !type.isObjective() || standardAnswer == null) {
            return false;
        }
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String normalized = Question.normalizeAnswer(type, answer);
        return normalized != null && standardAnswer.equals(normalized);
    }
}
