package com.bluewhale.oa.collab.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会议参会人快照。
 *
 * @author BlueWhale
 */
@Data
public class MeetingParticipant {

    /** 主键ID */
    private Long id;

    /** 会议ID */
    private Long meetingId;

    /** 参会人用户ID */
    private Long userId;

    /** 参会人姓名快照 */
    private String userName;

    /** 是否必到：0 否 / 1 是 */
    private Integer required;

    /** 参会状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
