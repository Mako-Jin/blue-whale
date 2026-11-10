package com.bluewhale.doc.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 评论已读状态实体，对应表 {@code doc_comment_read}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("doc_comment_read")
public class DocCommentRead extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 页面ID */
    private Long pageId;

    /** 用户ID */
    private Long userId;

    /** 最后已读评论ID */
    private Long lastReadCommentId;

    /** 最后已读时间 */
    private LocalDateTime lastReadAt;
}
