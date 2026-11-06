package com.bluewhale.lms.training.application.view;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 培训完成认定明细（一名学员一行）。
 *
 * <p>{@code basis} 说明认定依据，取值：</p>
 * <ul>
 *   <li>{@code ATTENDANCE}：全部场次已签到；</li>
 *   <li>{@code COURSE_PROGRESS}：无场次，关联课程全部章节均已完成；</li>
 *   <li>{@code ATTENDANCE_AND_COURSE}：既有场次又有课程，签到与进度同时满足；</li>
 *   <li>{@code ATTENDANCE_PENDING} / {@code COURSE_PROGRESS_PENDING}：对应条件未满足；</li>
 *   <li>{@code NO_BASIS}：班级既无场次又未关联课程，无法认定。</li>
 * </ul>
 *
 * @author BlueWhale
 */
@Data
public class CompletionItemView {

    /** 学员用户 ID */
    private Long userId;

    /** 学员用户名 */
    private String username;

    /** 分配时间 */
    private LocalDateTime assignedAt;

    /** 班级场次总数 */
    private int sessionsTotal;

    /** 该学员已签到的场次数（去重） */
    private int sessionsAttended;

    /** 关联课程的最低章节进度（0–100，未关联课程为 null） */
    private Integer coursePercent;

    /** 是否认定完成 */
    private boolean completed;

    /** 认定依据 */
    private String basis;
}
