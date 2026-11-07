package com.bluewhale.oa.document.domain.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 收文单据。
 *
 * @author BlueWhale
 */
@Data
public class DocIncoming {

    /** 主键ID */
    private Long id;

    /** 收文号 */
    private String incomingNo;

    /** 来文单位 */
    private String sourceUnit;

    /** 来文文号 */
    private String sourceNo;

    /** 公文标题 */
    private String title;

    /** 密级：1 公开 / 2 内部 / 3 秘密 */
    private Integer securityLevel;

    /** 紧急度：1 平件 / 2 急件 / 3 特急 */
    private Integer urgency;

    /** 收文日期 */
    private LocalDate receivedDate;

    /** 办理期限 */
    private LocalDate dueDate;

    /** 承办人用户ID */
    private Long assigneeId;

    /** 承办人姓名快照 */
    private String assigneeName;

    /** 批阅意见 */
    private String instruction;

    /** 办结时间 */
    private LocalDateTime handledAt;

    /** 状态 */
    private String status;

    /** 档案条目ID */
    private String archiveItemId;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
