package com.bluewhale.lms.course.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 章节学习进度实体，对应表 {@code lr_progress}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lr_progress")
public class ProgressEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 学习记录 ID */
    private Long enrollmentId;

    /** 章节 ID */
    private Long chapterId;

    /** 完成百分比（0–100，只增不减） */
    private Integer percent;

    /** 最近学习时间 */
    private LocalDateTime lastLearnedAt;
}
