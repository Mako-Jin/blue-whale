package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 检索索引覆盖统计（{@code fm_thread} 总数 / 已回填纯文本数）。
 *
 * @author BlueWhale
 */
@Data
public class IndexStat implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前租户未删除帖子总数 */
    private Long total;

    /** 已回填 {@code content_text} 的帖子数 */
    private Long indexed;
}
