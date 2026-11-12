package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 被引用楼层的摘要视图（F-04-02）。
 *
 * <p><b>为什么只回摘要不回全文</b>：引用块在前端只是一行灰色提示，
 * 回全文既浪费带宽，又会让「引用」变成绕过删帖看内容的通道——
 * 被引楼层一旦删除/待审，这里只剩 {@code deleted=true}，正文不返回。</p>
 *
 * @author BlueWhale
 */
@Data
public class QuotedPostVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 被引用楼层ID */
    private Long postId;

    /** 被引用楼层号（已删除时为 {@code null}） */
    private Integer floor;

    /** 被引用楼层作者（已删除时为 {@code null}） */
    private Long authorId;

    /** 摘要（已删除时为 {@code null}） */
    private String excerpt;

    /** 是否已删除/不可见：为 {@code true} 时前端展示「该楼层已删除」 */
    private Boolean deleted;
}
