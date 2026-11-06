package com.bluewhale.lms.exam.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 题目选项实体，对应表 {@code lr_question_option}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lr_question_option")
public class QuestionOptionEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 题目 ID */
    private Long questionId;

    /** 选项键 */
    private String optionKey;

    /** 选项内容 */
    private String content;

    /** 是否正确答案：0 否 / 1 是 */
    private Integer isCorrect;

    /** 排序号 */
    private Integer sortNo;
}
