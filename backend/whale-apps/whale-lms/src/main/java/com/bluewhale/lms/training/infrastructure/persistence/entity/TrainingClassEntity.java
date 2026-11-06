package com.bluewhale.lms.training.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 培训班实体，对应表 {@code lr_training_class}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lr_training_class")
public class TrainingClassEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 培训班编码 */
    private String classCode;

    /** 培训班名称 */
    private String title;

    /** 关联课程 ID */
    private Long courseId;

    /** 形式 */
    private String mode;

    /** 主讲讲师 */
    private String lecturer;

    /** 开班日期 */
    private LocalDate startDate;

    /** 结班日期 */
    private LocalDate endDate;

    /** 培训地点 */
    private String location;

    /** 计划人数 */
    private Integer capacity;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
