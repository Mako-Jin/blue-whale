package com.bluewhale.forum.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 处置/审核轨迹实体，对应表 {@code fm_moderation_record}。
 *
 * <p><b>append-only，不继承 BaseEntity</b>：表里没有 {@code update_time} 与
 * {@code deleted}，继承基类会让 MyBatis-Plus 往不存在的列写值（启动不报错、
 * 一写就 SQL 异常）。轨迹只增不改，正是审计要的语义。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("fm_moderation_record")
public class ForumModerationRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 对象类型：THREAD / POST / USER */
    private String targetType;

    /** 对象ID */
    private Long targetId;

    /** 动作：AUDIT_PASS / AUDIT_REJECT / PIN / ESSENCE / LOCK / REMOVE / MUTE / BAN */
    private String action;

    /** 原因/审核意见 */
    private String reason;

    /** 操作人 */
    private Long operatorId;

    /** 操作时间 */
    private LocalDateTime createTime;
}
