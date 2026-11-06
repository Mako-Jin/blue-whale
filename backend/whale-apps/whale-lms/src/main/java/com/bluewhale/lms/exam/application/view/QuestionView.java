package com.bluewhale.lms.exam.application.view;

import com.bluewhale.lms.exam.domain.model.Question;
import com.bluewhale.lms.exam.domain.model.QuestionOption;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目视图（管理端，含答案与解析）。
 *
 * @author BlueWhale
 */
@Data
public class QuestionView {

    /** 主键 */
    private Long id;

    /** 题目编码 */
    private String questionCode;

    /** 分类 */
    private String category;

    /** 题型 */
    private String type;

    /** 难度 */
    private String difficulty;

    /** 标签 */
    private List<String> tags;

    /** 题干 */
    private String stem;

    /** 标准答案 */
    private String standardAnswer;

    /** 答案解析 */
    private String analysis;

    /** 默认分值 */
    private BigDecimal defaultScore;

    /** 状态 */
    private String status;

    /** 选项 */
    private List<OptionView> options;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 领域转视图。
     *
     * @param question 题目
     * @return 视图
     */
    public static QuestionView from(Question question) {
        QuestionView view = new QuestionView();
        view.setId(question.getId());
        view.setQuestionCode(question.getQuestionCode());
        view.setCategory(question.getCategory());
        view.setType(question.getType() == null ? null : question.getType().name());
        view.setDifficulty(question.getDifficulty() == null ? null : question.getDifficulty().name());
        view.setTags(question.getTags());
        view.setStem(question.getStem());
        view.setStandardAnswer(question.getStandardAnswer());
        view.setAnalysis(question.getAnalysis());
        view.setDefaultScore(question.getDefaultScore());
        view.setStatus(question.getStatus() == null ? null : question.getStatus().name());
        view.setCreateTime(question.getCreateTime());
        view.setOptions(question.getOptions().stream().map(OptionView::from).toList());
        return view;
    }

    /**
     * 选项视图。
     *
     * @author BlueWhale
     */
    @Data
    public static class OptionView {

        /** 选项键 */
        private String optionKey;

        /** 选项内容 */
        private String content;

        /** 是否正确答案 */
        private boolean correct;

        /** 排序号 */
        private Integer sortNo;

        /**
         * 领域转视图。
         *
         * @param option 选项
         * @return 视图
         */
        public static OptionView from(QuestionOption option) {
            OptionView view = new OptionView();
            view.setOptionKey(option.getOptionKey());
            view.setContent(option.getContent());
            view.setCorrect(option.isCorrect());
            view.setSortNo(option.getSortNo());
            return view;
        }
    }
}
