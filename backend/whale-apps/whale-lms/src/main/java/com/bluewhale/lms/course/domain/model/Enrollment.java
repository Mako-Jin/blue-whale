package com.bluewhale.lms.course.domain.model;

import lombok.Data;

/**
 * 学习报名记录。
 *
 * <p>报名幂等：同一（租户、课程、学员）仅一条记录，重复报名返回既有记录。</p>
 *
 * @author BlueWhale
 */
@Data
public class Enrollment {

    /** 主键 */
    private Long id;

    /** 课程 ID */
    private Long courseId;

    /** 学员用户 ID */
    private Long userId;

    /** 学员用户名（冗余，展示用） */
    private String username;

    /** 报名时间 */
    private java.time.LocalDateTime enrolledAt;
}
