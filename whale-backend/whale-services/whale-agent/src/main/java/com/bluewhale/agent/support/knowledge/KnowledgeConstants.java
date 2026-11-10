package com.bluewhale.agent.support.knowledge;

/**
 * 文件知识库常量：状态机、来源、启用标记与上限。
 *
 * @author BlueWhale
 */
public final class KnowledgeConstants {

    /** 状态：待处理 */
    public static final String STATUS_PENDING = "PENDING";

    /** 状态：索引中 */
    public static final String STATUS_INDEXING = "INDEXING";

    /** 状态：已索引 */
    public static final String STATUS_INDEXED = "INDEXED";

    /** 状态：跳过（不支持/删除/停用/损坏） */
    public static final String STATUS_SKIPPED = "SKIPPED";

    /** 状态：失败（可重试，超过上限后终止） */
    public static final String STATUS_FAILED = "FAILED";

    /** 来源：文件服务业务文件 */
    public static final String SOURCE_FILE = "FILE";

    /** 来源：档案快照 */
    public static final String SOURCE_ARCHIVE = "ARCHIVE";

    /** 来源：whale-doc 文档页面 */
    public static final String SOURCE_DOC = "DOC";

    /** 启用 */
    public static final int ENABLED_YES = 1;

    /** 停用 */
    public static final int ENABLED_NO = 0;

    /** 失败原因最大存储长度 */
    public static final int MAX_ERROR_LENGTH = 500;

    /** 文件知识块索引路径前缀（由 FileKnowledgeSyncService 独立管理生命周期） */
    public static final String FILE_PATH_PREFIX = "file:";

    /** 文档知识块索引路径前缀（由 DocKnowledgeSyncService 独立管理生命周期） */
    public static final String DOC_PATH_PREFIX = "docpage:";

    /** 知识类型：文档页面（Lucene kind 字段） */
    public static final String KIND_DOC_PAGE = "doc_page";

    /** 文档可收集条件：已发布 */
    public static final String DOC_STATUS_PUBLISHED = "PUBLISHED";

    /** 文档可收集条件：公开空间 */
    public static final String DOC_SPACE_PUBLIC = "PUBLIC";

    /** 工具类不允许实例化 */
    private KnowledgeConstants() {
    }
}
