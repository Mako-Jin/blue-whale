package com.bluewhale.lms.training.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 讲师课时台账实体，对应表 {@code lr_lecturer_hours}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lr_lecturer_hours")
public class LecturerHoursEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 培训班 ID */
    private Long classId;

    /** 场次 ID */
    private Long sessionId;

    /** 讲师 */
    private String lecturer;

    /** 本节课时 */
    private BigDecimal hours;

    /** 入账时间 */
    private LocalDateTime accumulatedAt;
}
