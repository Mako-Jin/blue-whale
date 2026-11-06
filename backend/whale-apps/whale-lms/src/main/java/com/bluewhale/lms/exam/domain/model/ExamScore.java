package com.bluewhale.lms.exam.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成绩（按版本留存，重判生成新版本）。
 *
 * @author BlueWhale
 */
@Data
public class ExamScore {

    /** 主键 */
    private Long id;

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

    /** 是否及格 */
    private boolean passed;

    /** 判分人 */
    private Long gradedBy;

    /** 判分时间 */
    private LocalDateTime gradedAt;

    /** 判分备注 */
    private String remark;
}
