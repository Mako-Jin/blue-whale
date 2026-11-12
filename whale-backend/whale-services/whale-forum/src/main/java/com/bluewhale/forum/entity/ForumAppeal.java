package com.bluewhale.forum.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 申诉工单实体，对应表 {@code fm_appeal}。
 *
 * <p>{@code target_type=BAN} 时 {@code target_id} 存被处置用户ID；
 * {@code THREAD} / {@code POST} 存内容ID。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fm_appeal")
public class ForumAppeal extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户ID */
    private Long tenantId;

    /** 申诉人用户ID */
    private Long userId;

    /** 对象类型：THREAD / POST / BAN */
    private String targetType;

    /** 对象ID */
    private Long targetId;

    /** 申诉理由 */
    private String reason;

    /** 状态：PENDING / APPROVED / REJECTED */
    private String status;

    /** 复核人 */
    private Long handlerId;

    /** 复核意见 */
    private String handleRemark;

    /** 复核时间 */
    private LocalDateTime handleTime;
}
