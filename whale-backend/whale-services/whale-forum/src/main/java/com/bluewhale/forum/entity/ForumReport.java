package com.bluewhale.forum.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 举报工单实体，对应表 {@code fm_report}。
 *
 * <p><b>与帖子状态是两条链路</b>：受理举报不自动改帖状态，处置动作另行记录
 * （{@code fm_moderation_record}）。这样「举报不成立」不会被误读成「帖子有问题」。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fm_report")
public class ForumReport extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户ID */
    private Long tenantId;

    /** 对象类型：THREAD / POST / USER */
    private String targetType;

    /** 对象ID */
    private Long targetId;

    /** 举报人 */
    private Long reporterId;

    /** 举报类型：SPAM / ABUSE / PORN / ILLEGAL / OTHER */
    private String reportType;

    /** 举报说明 */
    private String description;

    /** 状态：PENDING / HANDLING / RESOLVED / REJECTED */
    private String status;

    /** 处理人 */
    private Long handlerId;

    /** 处理意见（REJECTED 时只通知举报人，不通知被举报人） */
    private String handleRemark;

    /** 处理时间 */
    private LocalDateTime handleTime;
}
