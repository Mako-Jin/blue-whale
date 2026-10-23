package com.bluewhale.agent.support.index;

/**
 * Lucene 索引字段名常量（与 {@link AgentIndexService} 的写入/检索保持一致）。
 *
 * @author BlueWhale
 */
public final class AgentIndexFields {

    /** 文档唯一ID：{@code kind:path:hash:seq} */
    public static final String ID = "id";

    /** 知识类型：code / doc / sql / config / git / runtime */
    public static final String KIND = "kind";

    /** 所属模块 */
    public static final String MODULE = "module";

    /** 仓库相对路径（存储 + 精确匹配） */
    public static final String PATH = "path";

    /** 路径分词字段（标识符检索） */
    public static final String PATH_TEXT = "path_text";

    /** 标题（中文分析器） */
    public static final String TITLE = "title";

    /** 标题分词字段（代码分析器） */
    public static final String TITLE_CODE = "title_code";

    /** 正文（中文分析器，存储） */
    public static final String CONTENT = "content";

    /** 正文分词字段（代码分析器，保留 camelCase/snake_case 原样） */
    public static final String CONTENT_CODE = "content_code";

    /** 起始行号（存储） */
    public static final String START_LINE = "start_line";

    /** 结束行号（存储） */
    public static final String END_LINE = "end_line";

    /** Wave 标记 */
    public static final String WAVE = "wave";

    /** 文件内容哈希（存储） */
    public static final String HASH = "hash";

    /** 可见性：public / runtime */
    public static final String VISIBILITY = "visibility";

    /** 向量字段（启用 embedding 时写入） */
    public static final String VECTOR = "vector";

    /** 向量维度（存储；0 表示该块未写向量） */
    public static final String VECTOR_DIM = "vector_dim";

    /** embedding 签名（存储，如 {@code hash:256}、{@code none}） */
    public static final String EMBED_SIG = "embed_sig";

    /** 工具类不允许实例化 */
    private AgentIndexFields() {
    }
}
