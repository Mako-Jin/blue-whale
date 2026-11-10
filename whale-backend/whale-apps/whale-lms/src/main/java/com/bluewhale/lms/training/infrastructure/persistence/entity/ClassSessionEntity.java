package com.bluewhale.lms.training.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 培训班场次实体，对应表 {@code lr_class_session}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lr_class_session")
public class ClassSessionEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属培训班 ID */
    private Long classId;

    /** 场次序号 */
    private Integer sessionNo;

    /** 场次主题 */
    private String title;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 场次地点 */
    private String location;

    /** 场次讲师 */
    private String lecturer;

    /** 本节课时 */
    private BigDecimal hours;
}
