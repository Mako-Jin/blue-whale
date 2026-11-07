package com.bluewhale.oa.attendance.domain.model;

import lombok.Data;

/**
 * 考勤组成员。
 *
 * @author BlueWhale
 */
@Data
public class AttendanceGroupMember {

    private Long id;

    /** 考勤组ID */
    private Long groupId;

    /** 用户ID */
    private Long userId;

    /** 用户姓名快照 */
    private String userName;
}
