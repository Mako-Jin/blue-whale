package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 待审队列条目。
 *
 * <p>帖子与楼层共用同一结构：{@code targetType} 区分，{@code title} 对楼层而言是
 * 所属帖子标题，{@code floor} 仅楼层有值。</p>
 *
 * @author BlueWhale
 */
@Data
public class AuditItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 对象类型：THREAD / POST */
    private String targetType;

    /** 对象ID */
    private Long targetId;

    /** 所属帖子ID（帖子本身时等于 targetId） */
    private Long threadId;

    /** 标题（楼层为所属帖子标题） */
    private String title;

    /** 内容摘要 */
    private String summary;

    /** 作者 */
    private Long authorId;

    /** 版块 */
    private Long boardId;

    /** 楼层号（仅楼层） */
    private Integer floor;

    /** 状态 */
    private String status;

    /** 提交时间 */
    private LocalDateTime createTime;
}
