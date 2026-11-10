package com.bluewhale.doc.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 页面评论实体，对应表 {@code doc_comment}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("doc_comment")
public class DocComment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 页面ID */
    private Long pageId;

    /** 父评论ID，0 表示主评论 */
    private Long parentId;

    /** TipTap 块ID（行内锚点） */
    private String anchorBlockId;

    /** 锚点摘要 */
    private String anchorText;

    /** 评论内容 */
    private String content;

    /** 评论人ID */
    private Long authorId;

    /** 解决状态：0 未解决 / 1 已解决 */
    private Integer resolved;

    /** 解决人ID */
    private Long resolvedBy;

    /** 解决时间 */
    private LocalDateTime resolvedAt;
}
