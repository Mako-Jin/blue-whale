package com.bluewhale.forum.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 禁言/封号实体，对应表 {@code fm_user_ban}。
 *
 * <p><b>这是禁言判定的唯一事实源</b>：只看 {@code status=ACTIVE} 且
 * {@code expireTime} 未到期，不靠「最新一条处置记录」推断——解禁、改期、
 * 永久禁言三种情况用「最新记录」都会算错。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fm_user_ban")
public class ForumUserBan extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户ID */
    private Long tenantId;

    /** 被处置用户ID */
    private Long userId;

    /** 类型：MUTE 禁言 / BAN 封号 */
    private String banType;

    /** 处置原因（必填） */
    private String reason;

    /** 到期时间（NULL 表示永久） */
    private LocalDateTime expireTime;

    /** 状态：ACTIVE / RELEASED */
    private String status;

    /** 操作人 */
    private Long operatorId;

    /** 来源举报工单ID */
    private Long sourceReportId;
}
