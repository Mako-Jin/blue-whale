package com.bluewhale.agent.support.ingest;

import lombok.Builder;
import lombok.Getter;

/**
 * 知识块：索引的最小检索单元。
 *
 * <p>由各类 {@link DocumentChunker} 从源文件切分产出，携带模块、标题路径与行号区间，
 * 供检索结果与证据面板精确定位。</p>
 *
 * @author BlueWhale
 */
@Getter
@Builder
public class Chunk {

    /** 知识类型：code / doc / sql / config / git / runtime */
    private final String kind;

    /** 所属模块（如 whale-auth、whale-admin），无法识别时为空串 */
    private final String module;

    /** 仓库相对路径（统一使用 / 分隔） */
    private final String path;

    /** 标题（类名 / 方法名 / 标题路径 / 表名 / 配置键） */
    private final String title;

    /** 正文内容 */
    private final String content;

    /** 起始行号（1 起） */
    private final int startLine;

    /** 结束行号（1 起） */
    private final int endLine;

    /** 关联 Wave 标记（无则为空串） */
    private final String wave;
}
