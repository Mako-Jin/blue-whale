package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 帖子列表查询条件。
 *
 * @author BlueWhale
 */
@Data
public class ThreadQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 版块ID（为空表示全站） */
    private Long boardId;

    /** 排序：latest 最新 / hot 热帖（按最后回复时间）/ essence 精华 */
    private String sort = "latest";

    /** 标题关键词（M1 走 LIKE，M3 换 ngram 全文索引） */
    private String keyword;

    /** 只看精华 */
    private Boolean essenceOnly;

    /** 页码（从 1 起） */
    private Long current = 1L;

    /** 每页条数 */
    private Long size = 20L;
}
