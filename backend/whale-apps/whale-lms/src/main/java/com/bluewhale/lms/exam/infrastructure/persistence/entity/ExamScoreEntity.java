package com.bluewhale.lms.exam.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成绩实体，对应表 {@code lr_score}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lr_score")
public class ExamScoreEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 作答记录 ID */
    private Long attemptId;

    /** 考试 ID */
    private Long examId;

    /** 学员用户 ID */
    private Long userId;

    /** 版本号 */
    private Integer versionNo;

    /** 客观题得分 */
    private BigDecimal objectiveScore;

    /** 主观题得分 */
    private BigDecimal subjectiveScore;

    /** 总分 */
    private BigDecimal totalScore;

    /** 是否及格：0 否 / 1 是 */
    private Integer passed;

    /** 判分人 */
    private Long gradedBy;

    /** 判分时间 */
    private LocalDateTime gradedAt;

    /** 判分备注 */
    private String remark;
}
