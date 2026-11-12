package com.bluewhale.forum.service;

import com.bluewhale.forum.dto.SearchStatusVO;

/**
 * 检索索引维护：状态自查与纯文本回填（重建）。
 *
 * <p>检索查询本身不在这里——它必须复用帖子的可见性判定（版块可见、状态可见、
 * 租户隔离），复制一份必然与列表页判定分叉。这里只负责「索引侧」：
 * 什么引擎、覆盖到哪、以及把历史帖的 {@code content_text} 补上。</p>
 *
 * @author BlueWhale
 */
public interface SearchService {

    /**
     * 当前检索状态（引擎、分词长度、覆盖条数）。
     *
     * @return 状态
     */
    SearchStatusVO status();

    /**
     * 回填 {@code content_text}（可重复执行，已回填的行不会再处理）。
     *
     * @param limit 本次最多处理条数，{@code null} 取默认批量
     * @return 实际回填条数
     */
    int reindex(Integer limit);

    /**
     * 该关键词是否达到走全文索引的长度。
     *
     * @param keyword 关键词
     * @return 达到返回 {@code true}，否则调用方回退 {@code LIKE}
     */
    boolean useFulltext(String keyword);

    /**
     * 走全文索引的最小关键词长度（= 数据库 ngram 分词长度）。
     *
     * @return 最小长度
     */
    int minKeywordLength();
}
