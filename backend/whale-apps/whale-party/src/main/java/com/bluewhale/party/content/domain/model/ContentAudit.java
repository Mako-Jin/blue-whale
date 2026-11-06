package com.bluewhale.party.content.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 党建内容审核轨迹。
 *
 * <p>每次状态流转都留一条：谁在什么时候、把内容从什么状态改成什么状态、意见是什么。
 * 退回理由不落库，事后追责只能看到「被退回过」，看不出为什么退。</p>
 *
 * @author BlueWhale
 */
@Data
public class ContentAudit {

    /** 主键 */
    private Long id;

    /** 内容 ID */
    private Long contentId;

    /** 动作 */
    private AuditAction action;

    /** 变更前状态 */
    private String fromStatus;

    /** 变更后状态 */
    private String toStatus;

    /** 审核意见（退回时必填） */
    private String opinion;

    /** 操作人用户 ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 创建时间 */
    private LocalDateTime createTime;
}
