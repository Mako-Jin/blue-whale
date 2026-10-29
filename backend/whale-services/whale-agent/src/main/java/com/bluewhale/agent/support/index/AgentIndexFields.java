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

    /** 文件服务文件ID（Wave 5.53 M4：业务文件知识块） */
    public static final String FILE_ID = "file_id";

    /** 文档页面ID（whale-doc 知识块） */
    public static final String DOC_PAGE_ID = "doc_page_id";

    /** 文档空间ID（whale-doc 知识块） */
    public static final String SPACE_ID = "space_id";

    /** 业务页面地址（whale-doc 知识块，用于检索结果跳转） */
    public static final String URL = "url";

    /** 知识块所需权限码（档案知识过滤；普通文件为空） */
    public static final String PERMISSION_CODE = "permission_code";

    /** 受限标记（仅受限知识块写入，用于精确判定是否存在权限约束） */
    public static final String PERMISSION_RESTRICTED = "permission_restricted";

    /** 租户ID（平台级知识为 0，业务知识为租户ID；检索时强制过滤） */
    public static final String TENANT = "tenant_id";

    /** 归属人ID（文件知识的上传者；缺省表示租户内公共知识，检索时按当前用户过滤） */
    public static final String OWNER_ID = "owner_id";

    /** 工具类不允许实例化 */
    private AgentIndexFields() {
    }
}
