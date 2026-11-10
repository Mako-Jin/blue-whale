package com.bluewhale.lms.training.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 必修分配记录（一人一班一条）。
 *
 * <p>唯一性由表约束 {@code uk_lr_assignment}（tenant_id + class_id + user_id）
 * 与应用层幂等查询共同保证，因此「批量分配」可重复执行而不产生重复报名。</p>
 *
 * @author BlueWhale
 */
@Data
public class TrainingAssignment {

    /** 主键 */
    private Long id;

    /** 培训班 ID */
    private Long classId;

    /** 分配范围 */
    private AssignmentScope scopeType;

    /** 范围取值（部门编码 / 角色编码，USER 为空） */
    private String scopeValue;

    /** 学员用户 ID */
    private Long userId;

    /** 学员用户名 */
    private String username;

    /** 分配时间 */
    private LocalDateTime assignedAt;
}
