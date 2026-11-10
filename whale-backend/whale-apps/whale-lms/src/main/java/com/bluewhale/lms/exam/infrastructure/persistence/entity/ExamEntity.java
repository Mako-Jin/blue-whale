package com.bluewhale.lms.exam.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 考试实体，对应表 {@code lr_exam}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lr_exam")
public class ExamEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 考试编码（租户内唯一） */
    private String examCode;

    /** 考试名称 */
    private String title;

    /** 试卷 ID */
    private Long paperId;

    /** 关联课程 ID */
    private Long courseId;

    /** 开考时间 */
    private LocalDateTime startTime;

    /** 截止时间 */
    private LocalDateTime endTime;

    /** 单次作答时长（分钟） */
    private Integer durationMinutes;

    /** 最大作答次数 */
    private Integer maxAttempts;

    /** 是否乱序：0 否 / 1 是 */
    private Integer shuffle;

    /** 及格分 */
    private BigDecimal passScore;

    /** 切屏次数上限 */
    private Integer switchLimit;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
