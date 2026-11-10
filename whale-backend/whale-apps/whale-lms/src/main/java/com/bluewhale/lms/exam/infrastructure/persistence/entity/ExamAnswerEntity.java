package com.bluewhale.lms.exam.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 作答明细实体，对应表 {@code lr_exam_answer}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lr_exam_answer")
public class ExamAnswerEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 作答记录 ID */
    private Long attemptId;

    /** 题目 ID */
    private Long questionId;

    /** 题型快照 */
    private String questionType;

    /** 学员作答 */
    private String answer;

    /** 是否正确：0 否 / 1 是 */
    private Integer correct;

    /** 本题得分 */
    private BigDecimal score;

    /** 是否已判：0 待判 / 1 已判 */
    private Integer graded;

    /** 呈现次序 */
    private Integer sortNo;
}
