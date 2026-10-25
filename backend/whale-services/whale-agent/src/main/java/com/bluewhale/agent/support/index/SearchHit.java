package com.bluewhale.agent.support.index;

import lombok.Builder;
import lombok.Getter;

/**
 * 检索命中结果（BM25 原始分数，供 {@code GET /agent/search} 调试与后续 RRF 融合使用）。
 *
 * @author BlueWhale
 */
@Getter
@Builder
public class SearchHit {

    /** 文档ID */
    private final String id;

    /** 知识类型 */
    private final String kind;

    /** 所属模块 */
    private final String module;

    /** 仓库相对路径 */
    private final String path;

    /** 标题 */
    private final String title;

    /** 正文片段 */
    private final String content;

    /** 起始行号 */
    private final int startLine;

    /** 结束行号 */
    private final int endLine;

    /** Wave 标记 */
    private final String wave;

    /** BM25 分数 */
    private final float score;

    /** 文件服务文件ID（普通知识块为 {@code null}） */
    private final Long fileId;

    /**
     * 复制命中并替换分数（RRF 融合使用，原分数不再具备可比性）。
     *
     * @param newScore 融合分数
     * @return 新命中对象
     */
    public SearchHit withScore(double newScore) {
        return SearchHit.builder()
                .id(id)
                .kind(kind)
                .module(module)
                .path(path)
                .title(title)
                .content(content)
                .startLine(startLine)
                .endLine(endLine)
                .wave(wave)
                .fileId(fileId)
                .score((float) newScore)
                .build();
    }
}
