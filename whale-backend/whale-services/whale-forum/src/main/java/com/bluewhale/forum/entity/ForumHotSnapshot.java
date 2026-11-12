package com.bluewhale.forum.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 热榜快照实体，对应表 {@code fm_hot_snapshot}。
 *
 * <p>派生数据：每次刷新在租户内<b>整表物理覆盖</b>（先删后插），不做逻辑删除——
 * 快照行一旦带 {@code deleted} 标记，唯一键 {@code uk(tenant_id, rank_no)} 会挡住
 * 下一轮同名次写入，表现为「刷新一次之后再也刷不进去」。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("fm_hot_snapshot")
public class ForumHotSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 热榜窗口：24h / 7d */
    private String hotWindow;

    /** 名次（1 起） */
    private Integer rankNo;

    /** 帖子ID */
    private Long threadId;

    /** 版块ID */
    private Long boardId;

    /** 标题（快照时点） */
    private String title;

    /** 摘要（快照时点） */
    private String summary;

    /** 作者用户ID */
    private Long authorId;

    /** 回复数（快照时点） */
    private Integer replyCount;

    /** 投花数（快照时点） */
    private Integer likeCount;

    /** 浏览数（快照时点） */
    private Integer viewCount;

    /** 热度分 */
    private BigDecimal score;

    /** 快照生成时间 */
    private LocalDateTime snapshotTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
