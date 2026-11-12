package com.bluewhale.forum.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @提及记录实体，对应表 {@code fm_mention}。
 *
 * <p>append-only，不继承 BaseEntity（表无 {@code update_time}/{@code deleted}）。
 * {@code postId} 为 {@code 0} 表示主帖（1 楼由 {@code fm_thread} 代表，不落 {@code fm_post}）；
 * 不用 {@code NULL} 是因为 MySQL 唯一键对 {@code NULL} 不去重，判重会静默失效。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("fm_mention")
public class ForumMention implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 帖子ID */
    private Long threadId;

    /** 楼层ID（0 表示主帖） */
    private Long postId;

    /** 被提及用户ID */
    private Long userId;

    /** 发起提及的用户ID */
    private Long mentionedBy;

    /** 来源：THREAD / POST */
    private String source;

    /** 提及时间 */
    private LocalDateTime createTime;
}
