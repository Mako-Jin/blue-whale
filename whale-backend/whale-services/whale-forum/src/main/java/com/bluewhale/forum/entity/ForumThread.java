package com.bluewhale.forum.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 论坛帖子实体（聚合根），对应表 {@code fm_thread}。
 *
 * <p>命名避开 {@link java.lang.Thread}，实体统一 {@code Forum*} 前缀。</p>
 *
 * <p><b>楼层号不回收</b>：{@code nextFloor} 由单语句 {@code LAST_INSERT_ID(expr)} 原子分配，
 * 楼层一经分配即使楼层被删也不回收，否则「N 楼」与引用全部错位。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fm_thread")
public class ForumThread extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户ID */
    private Long tenantId;

    /** 所属版块ID */
    private Long boardId;

    /** 帖子编号（FM-yyyyMMdd-XXXX，软删改写为 原编号#D{id}） */
    private String threadNo;

    /** 类型：NORMAL/QA/VOTE/NOTICE/ACTIVITY/ARTICLE */
    private String threadType;

    /** 标题 */
    private String title;

    /** 正文（已消毒富文本源码） */
    private String content;

    /** 正文纯文本（检索用，写入时由消毒后 HTML 提取；全文索引建在这列而不是 content） */
    private String contentText;

    /** 摘要（列表与搜索高亮用） */
    private String summary;

    /** 标签，逗号分隔 */
    private String tags;

    /** 作者用户ID（匿名帖也恒存） */
    private Long authorId;

    /** 是否匿名展示：0 否 / 1 是 */
    private Integer anonymous;

    /** 匿名编号 */
    private String anonymousNo;

    /** 状态：DRAFT/SCHEDULED/PENDING/PUBLISHED/SOLVED/CLOSED/REJECTED/DELETED */
    private String status;

    /** 悬榜（置顶）：0 否 / 1 是 */
    private Integer pinned;

    /** 题金榜（精华）：0 否 / 1 是 */
    private Integer essence;

    /** 浏览数 */
    private Integer viewCount;

    /** 回复数（DB 为准，每日对账） */
    private Integer replyCount;

    /** 投花数 */
    private Integer likeCount;

    /** 收藏数 */
    private Integer collectCount;

    /** 下一个可用楼层号（不回收） */
    private Integer nextFloor;

    /** 最后回复时间 */
    private LocalDateTime lastReplyTime;

    /** 最后回复人 */
    private Long lastReplyUserId;

    /** 发布时间 */
    private LocalDateTime publishTime;

    /** 审核/退回意见 */
    private String auditRemark;
}
