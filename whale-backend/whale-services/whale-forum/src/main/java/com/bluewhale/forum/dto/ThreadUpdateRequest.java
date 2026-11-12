package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 帖子编辑请求。
 *
 * <p>审核版块内编辑会把帖子退回 {@code PENDING}（规格 §6.6），
 * {@code reason} 为编辑说明，进编辑历史，便于审核人对比。</p>
 *
 * @author BlueWhale
 */
@Data
public class ThreadUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 新标题 */
    private String title;

    /** 新正文（富文本源码，服务端消毒） */
    private String content;

    /** 新标签（逗号分隔；{@code null} 表示不改，空串表示清空） */
    private String tags;

    /** 编辑说明（进编辑历史） */
    private String reason;
}
