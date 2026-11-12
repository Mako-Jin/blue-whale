package com.bluewhale.forum.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 帖子标签实体，对应表 {@code fm_thread_tag}。
 *
 * <p>append-only，不继承 BaseEntity（表无 {@code update_time}/{@code deleted}）。
 * 标签以「行」存储只为一件事：能 {@code GROUP BY} 聚合、能按标签精确筛选；
 * {@code fm_thread.tags} 逗号串继续作为列表展示快照。改标签走「删旧行 + 插新行」，
 * 不做差量比对——并发编辑下差量会漏行，而单帖标签最多十几个。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("fm_thread_tag")
public class ForumThreadTag implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法；迁移回填用 UUID_SHORT） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 帖子ID */
    private Long threadId;

    /** 标签 */
    private String tag;

    /** 创建时间 */
    private LocalDateTime createTime;
}
