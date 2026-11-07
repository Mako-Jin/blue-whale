package com.bluewhale.oa.collab.domain.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 会议行动项。
 *
 * <p>{@code taskId} 回填后表示已生成个人任务。</p>
 *
 * @author BlueWhale
 */
@Data
public class MeetingAction {

    /** 主键ID */
    private Long id;

    /** 会议ID */
    private Long meetingId;

    /** 行动项内容 */
    private String content;

    /** 负责人用户ID */
    private Long ownerId;

    /** 负责人姓名快照 */
    private String ownerName;

    /** 截止日期 */
    private LocalDate dueDate;

    /** 状态：OPEN / DONE */
    private String status;

    /** 生成的个人任务ID */
    private Long taskId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
