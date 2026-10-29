package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 页面评论未读视图。
 *
 * @author BlueWhale
 */
@Data
public class DocCommentUnreadVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 未读评论数 */
    private long unread;

    /** 最后已读评论ID */
    private Long lastReadCommentId;
}
