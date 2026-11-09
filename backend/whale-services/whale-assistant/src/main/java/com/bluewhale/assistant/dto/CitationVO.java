package com.bluewhale.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 引用视图对象：回答中的 {@code [S1]} 对应一条引用（结构与 whale-ai SSE done 帧对齐）。
 *
 * <p>需要从 {@code asst_message.citations_json} 反序列化回放，因此保留无参构造。</p>
 *
 * @author BlueWhale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitationVO {

    /** 引用编号（S1、S2…） */
    private String sourceId;

    /** 证据类型：fact / chunk */
    private String kind;

    /** 来源路径（仓库相对路径或 git:/runtime: 虚拟来源） */
    private String path;

    /** 标题 */
    private String title;

    /** 起始行号 */
    private Integer startLine;

    /** 结束行号 */
    private Integer endLine;

    /** 证据片段 */
    private String snippet;

    /** 事实类型（仅 fact） */
    private String factType;

    /** 事实键（仅 fact） */
    private String factKey;

    /** 检索分数（仅 chunk） */
    private Double score;
}
