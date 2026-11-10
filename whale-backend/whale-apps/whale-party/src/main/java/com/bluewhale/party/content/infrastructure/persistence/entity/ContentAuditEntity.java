package com.bluewhale.party.content.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 党建内容审核轨迹实体，对应表 {@code pty_content_audit}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pty_content_audit")
public class ContentAuditEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 内容 ID */
    private Long contentId;

    /** 动作 */
    private String action;

    /** 变更前状态 */
    private String fromStatus;

    /** 变更后状态 */
    private String toStatus;

    /** 审核意见 */
    private String opinion;

    /** 操作人用户 ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;
}
