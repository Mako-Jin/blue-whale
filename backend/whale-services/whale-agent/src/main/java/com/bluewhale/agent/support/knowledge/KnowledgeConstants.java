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

    /** 启用 */
    public static final int ENABLED_YES = 1;

    /** 停用 */
    public static final int ENABLED_NO = 0;

    /** 失败原因最大存储长度 */
    public static final int MAX_ERROR_LENGTH = 500;

    /** 工具类不允许实例化 */
    private KnowledgeConstants() {
    }
}
