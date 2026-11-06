package com.bluewhale.lms.training.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 必修分配实体，对应表 {@code lr_assignment}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lr_assignment")
public class TrainingAssignmentEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 培训班 ID */
    private Long classId;

    /** 分配范围 */
    private String scopeType;

    /** 范围取值 */
    private String scopeValue;

    /** 学员用户 ID */
    private Long userId;

    /** 学员用户名 */
    private String username;

    /** 分配时间 */
    private LocalDateTime assignedAt;
}
