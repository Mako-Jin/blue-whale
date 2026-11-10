package com.bluewhale.lms.exam.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 试卷题目实体，对应表 {@code lr_paper_question}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lr_paper_question")
public class PaperQuestionEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 试卷 ID */
    private Long paperId;

    /** 题目 ID */
    private Long questionId;

    /** 本题分值 */
    private BigDecimal score;

    /** 题序 */
    private Integer sortNo;

    /** 来源：MANUAL / RULE */
    private String source;
}
