package com.bluewhale.forum.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 论坛互动实体（投花 / 藏帖），对应表 {@code fm_reaction}。
 *
 * <p><b>物理删除，不挂 {@code deleted}</b>：唯一键
 * {@code uk_fm_reaction(tenant_id, user_id, target_type, target_id, reaction_type)}
 * 只在「一人一次」时成立；若改逻辑删除，取消后再点会撞唯一键，
 * 表现为「取消过就再也投不了花」。表也没有 {@code update_time}，故不继承 BaseEntity。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("fm_reaction")
public class ForumReaction implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 操作用户ID */
    private Long userId;

    /** 目标类型：THREAD 帖子 / POST 楼层 */
    private String targetType;

    /** 目标ID */
    private Long targetId;

    /** 互动类型：LIKE 投花 / COLLECT 藏帖 */
    private String reactionType;

    /** 互动时间 */
    private LocalDateTime createTime;
}
