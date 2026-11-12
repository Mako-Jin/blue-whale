package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 帖子列表项视图（列表页字段，不含正文）。
 *
 * <p>作者只给 {@code authorId}：平台没有跨服务用户名查询 SDK，前端用「鲸友 #id」占位，
 * 待 auth 提供内网通讯录批量接口后统一补 {@code authorName}。</p>
 *
 * @author BlueWhale
 */
@Data
public class ThreadListItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 帖子ID */
    private Long id;

    /** 版块ID */
    private Long boardId;

    /** 帖子编号 */
    private String threadNo;

    /** 类型 */
    private String threadType;

    /** 标题 */
    private String title;

    /** 摘要 */
    private String summary;

    /** 作者用户ID */
    private Long authorId;

    /** 是否匿名展示：0 否 / 1 是 */
    private Integer anonymous;

    /** 匿名编号 */
    private String anonymousNo;

    /** 状态 */
    private String status;

    /** 悬榜：0 否 / 1 是 */
    private Integer pinned;

    /** 题金榜：0 否 / 1 是 */
    private Integer essence;

    /** 回复数 */
    private Integer replyCount;

    /** 浏览数 */
    private Integer viewCount;

    /** 投花数 */
    private Integer likeCount;

    /** 收藏数 */
    private Integer collectCount;

    /** 最后回复时间 */
    private LocalDateTime lastReplyTime;

    /** 发布时间 */
    private LocalDateTime publishTime;
}
