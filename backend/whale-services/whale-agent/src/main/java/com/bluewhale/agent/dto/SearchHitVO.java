package com.bluewhale.agent.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 检索命中视图对象（{@code GET /agent/search} 响应元素）。
 *
 * @author BlueWhale
 */
@Data
@Builder
public class SearchHitVO {

    /** 文档ID */
    private String id;

    /** 知识类型 */
    private String kind;

    /** 所属模块 */
    private String module;

    /** 仓库相对路径 */
    private String path;

    /** 标题 */
    private String title;

    /** 正文片段 */
    private String content;

    /** 起始行号 */
    private Integer startLine;

    /** 结束行号 */
    private Integer endLine;

    /** Wave 标记 */
    private String wave;

    /** 业务页面地址（文档知识块可跳转） */
    private String url;

    /** BM25 分数 */
    private Float score;
}
