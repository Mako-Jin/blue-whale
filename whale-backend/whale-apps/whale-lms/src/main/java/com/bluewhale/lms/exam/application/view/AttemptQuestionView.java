package com.bluewhale.lms.exam.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 作答卷面中的单题视图。
 *
 * <p><b>这里刻意没有标准答案，也没有选项的 {@code correct} 标记。</b>
 * 卷面接口在作答期间对学员开放，一旦把正确标记带出去，前端 F12 就能直接抄答案。
 * 选项只回传键与内容，正确与否在交卷后由独立的结果接口按题给出。</p>
 *
 * @author BlueWhale
 */
@Data
public class AttemptQuestionView {

    /** 题目 ID */
    private Long questionId;

    /** 题型 */
    private String type;

    /** 题干 */
    private String stem;

    /** 本题分值 */
    private BigDecimal score;

    /** 呈现次序 */
    private Integer sortNo;

    /** 已保存的作答 */
    private String answer;

    /** 选项（单选 / 多选） */
    private List<OptionView> options = new ArrayList<>();

    /**
     * 选项视图（不含正确标记）。
     *
     * @author BlueWhale
     */
    @Data
    public static class OptionView {

        /** 选项键 */
        private String optionKey;

        /** 选项内容 */
        private String content;
    }
}
