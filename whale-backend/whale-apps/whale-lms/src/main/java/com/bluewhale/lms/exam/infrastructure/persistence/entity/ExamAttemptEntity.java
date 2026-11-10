package com.bluewhale.lms.exam.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 作答记录实体，对应表 {@code lr_exam_attempt}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lr_exam_attempt")
public class ExamAttemptEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 考试 ID */
    private Long examId;

    /** 试卷 ID（快照） */
    private Long paperId;

    /** 学员用户 ID */
    private Long userId;

    /** 学员用户名 */
    private String username;

    /** 第几次作答 */
    private Integer attemptNo;

    /** 乱序种子 */
    private String seed;

    /** 开始作答时间 */
    private LocalDateTime startedAt;

    /** 本次截止时间 */
    private LocalDateTime deadlineAt;

    /** 交卷时间 */
    private LocalDateTime submittedAt;

    /** 状态 */
    private String status;

    /** 客观题得分 */
    private BigDecimal objectiveScore;

    /** 主观题得分 */
    private BigDecimal subjectiveScore;

    /** 总分 */
    private BigDecimal totalScore;

    /** 是否及格：0 否 / 1 是 */
    private Integer passed;

    /** 切屏次数 */
    private Integer switchCount;
}
