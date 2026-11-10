package com.bluewhale.lms.course.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 学习报名实体，对应表 {@code lr_enrollment}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lr_enrollment")
public class EnrollmentEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 课程 ID */
    private Long courseId;

    /** 学员用户 ID */
    private Long userId;

    /** 学员用户名 */
    private String username;

    /** 报名时间 */
    private LocalDateTime enrolledAt;
}
