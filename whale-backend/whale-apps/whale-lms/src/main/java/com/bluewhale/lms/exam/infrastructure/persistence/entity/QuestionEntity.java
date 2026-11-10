package com.bluewhale.lms.exam.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 题目实体，对应表 {@code lr_question}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lr_question")
public class QuestionEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 题目编码（租户内唯一） */
    private String questionCode;

    /** 分类 */
    private String category;

    /** 题型 */
    private String questionType;

    /** 难度 */
    private String difficulty;

    /** 标签（逗号分隔） */
    private String tags;

    /** 题干 */
    private String stem;

    /** 标准答案（已归一） */
    private String standardAnswer;

    /** 答案解析 */
    private String analysis;

    /** 默认分值 */
    private BigDecimal defaultScore;

    /** 状态 */
    private String status;
}
