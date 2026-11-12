package com.bluewhale.forum.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 浏览去重实体，对应表 {@code fm_thread_view}。
 *
 * <p>一行 = 一个 {@code (租户, 帖子, 用户)} 组合，{@code last_view_time} 是滚动窗口基准：
 * 距上次计入浏览不足 24 小时就不再累加 {@code fm_thread.view_count}，
 * 否则刷新页面就能把浏览数刷上天。</p>
 *
 * <p>不继承 BaseEntity：表无 {@code update_time}/{@code deleted}，
 * 去重行靠唯一键覆盖写，逻辑删除只会让唯一键失去约束力。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("fm_thread_view")
public class ForumThreadView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 帖子ID */
    private Long threadId;

    /** 浏览人用户ID */
    private Long userId;

    /** 最近一次计入浏览的时间 */
    private LocalDateTime lastViewTime;
}
