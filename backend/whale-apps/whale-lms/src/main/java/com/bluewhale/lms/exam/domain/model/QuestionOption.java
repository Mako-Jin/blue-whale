package com.bluewhale.lms.exam.domain.model;

import lombok.Data;

/**
 * 题目选项（单选 / 多选使用）。
 *
 * @author BlueWhale
 */
@Data
public class QuestionOption {

    /** 主键 */
    private Long id;

    /** 题目 ID */
    private Long questionId;

    /** 选项键（A/B/C…） */
    private String optionKey;

    /** 选项内容 */
    private String content;

    /** 是否正确答案 */
    private boolean correct;

    /** 排序号 */
    private Integer sortNo;
}
