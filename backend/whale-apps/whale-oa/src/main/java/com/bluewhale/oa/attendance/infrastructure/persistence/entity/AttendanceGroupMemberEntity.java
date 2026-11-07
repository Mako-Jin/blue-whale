package com.bluewhale.oa.attendance.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 考勤组成员实体，对应表 {@code oa_att_group_member}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_att_group_member")
public class AttendanceGroupMemberEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 考勤组ID */
    private Long groupId;

    /** 用户ID */
    private Long userId;

    /** 用户姓名快照 */
    private String userName;
}
