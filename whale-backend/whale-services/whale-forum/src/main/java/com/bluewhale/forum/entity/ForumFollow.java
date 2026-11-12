package com.bluewhale.forum.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 关注关系实体，对应表 {@code fm_follow}。
 *
 * <p>关系表不继承 BaseEntity（无 {@code update_time} / {@code deleted}）：
 * 取关即物理删行，软删会让唯一键挡住「取关后再关注」。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("fm_follow")
public class ForumFollow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 关注人用户ID */
    private Long userId;

    /** 关注对象类型：USER / BOARD */
    private String targetType;

    /** 关注对象ID */
    private Long targetId;

    /** 关注时间 */
    private LocalDateTime createTime;
}
