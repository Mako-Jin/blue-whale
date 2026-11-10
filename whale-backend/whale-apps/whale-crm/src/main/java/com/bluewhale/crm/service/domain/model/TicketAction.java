package com.bluewhale.crm.service.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单处理记录（追加式，不更新不删除）。
 *
 * @author BlueWhale
 */
@Data
public class TicketAction {

    /** 主键 */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 工单 ID */
    private Long ticketId;

    /** 动作类型 */
    private TicketActionType actionType;

    /** 变更前状态 */
    private TicketStatus fromStatus;

    /** 变更后状态 */
    private TicketStatus toStatus;

    /** 操作人 ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 动作内容 */
    private String content;

    /** 记录时间 */
    private LocalDateTime createdAt;
}
