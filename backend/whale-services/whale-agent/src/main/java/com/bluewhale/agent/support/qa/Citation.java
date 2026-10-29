package com.bluewhale.agent.support.qa;

/**
 * 证据引用（回答中的 {@code [S1]}）。
 *
 * <p>{@code kind} 为 {@code fact}（结构化事实）或 {@code chunk}（全文知识块）；
 * {@code path} 为仓库相对路径或 {@code git:}/{@code runtime:} 虚拟来源；
 * 行号仅知识块有值。</p>
 *
 * @param sourceId  引用编号（S1、S2…）
 * @param kind      证据类型：fact / chunk
 * @param path      来源路径
 * @param title     标题（服务名、表名、文档标题等）
 * @param startLine 起始行（可空）
 * @param endLine   结束行（可空）
 * @param snippet   证据片段（已截断）
 * @param factType  事实类型（仅 fact）
 * @param factKey   事实键（仅 fact）
 * @param score     检索分数（仅 chunk）
 * @param fileId    文件服务文件ID（业务文件/档案知识块，可空）
 * @param url       业务页面地址（文档知识块可跳转，可空）
 * @author BlueWhale
 */
public record Citation(String sourceId, String kind, String path, String title,
                       Integer startLine, Integer endLine, String snippet,
                       String factType, String factKey, Double score, Long fileId, String url) {

    /**
     * 兼容旧调用的构造器（无业务页面地址）。
     *
     * @param sourceId  引用编号
     * @param kind      证据类型
     * @param path      来源路径
     * @param title     标题
     * @param startLine 起始行
     * @param endLine   结束行
     * @param snippet   证据片段
     * @param factType  事实类型
     * @param factKey   事实键
     * @param score     检索分数
     * @param fileId    文件ID
     */
    public Citation(String sourceId, String kind, String path, String title,
                    Integer startLine, Integer endLine, String snippet,
                    String factType, String factKey, Double score, Long fileId) {
        this(sourceId, kind, path, title, startLine, endLine, snippet, factType, factKey, score, fileId, null);
    }
}
