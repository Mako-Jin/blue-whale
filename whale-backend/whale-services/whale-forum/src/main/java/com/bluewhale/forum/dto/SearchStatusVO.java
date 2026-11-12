package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 检索状态（运维自查用）。
 *
 * @author BlueWhale
 */
@Data
public class SearchStatusVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 检索引擎：mysql-ngram */
    private String engine;

    /** 全文索引名 */
    private String indexName;

    /** ngram 分词长度（MySQL 变量 ngram_token_size，默认 2） */
    private Integer ngramTokenSize;

    /** 走全文索引的最小关键词长度（= 分词长度） */
    private Integer minKeywordLength;

    /** 是否启用单字回退 LIKE（长度不足时） */
    private Boolean likeFallback;

    /** 当前租户帖子总数 */
    private Long totalThreads;

    /** 已回填纯文本（已可被检索）的帖子数 */
    private Long indexedThreads;

    /** 待重建条数（= 总数 − 已回填） */
    private Long pendingBackfill;
}
