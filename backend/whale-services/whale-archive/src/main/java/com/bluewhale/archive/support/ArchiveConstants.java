package com.bluewhale.archive.support;

/**
 * 档案服务通用常量。
 *
 * <p>集中定义状态、密级、文件角色与来源类型，避免多处魔法值漂移。</p>
 *
 * @author BlueWhale
 */
public final class ArchiveConstants {

    /** 总启用状态：启用 */
    public static final int STATUS_ENABLED = 1;

    /** 密级下限：公开 */
    public static final int SECURITY_PUBLIC = 1;

    /** 密级：内部（默认） */
    public static final int SECURITY_INTERNAL = 2;

    /** 密级上限：秘密 */
    public static final int SECURITY_SECRET = 3;

    /** 档案条目状态：待整理 */
    public static final int ITEM_STATUS_PENDING = 0;

    /** 档案条目状态：已归档 */
    public static final int ITEM_STATUS_ARCHIVED = 1;

    /** 档案条目状态：已销毁 */
    public static final int ITEM_STATUS_DESTROYED = 2;

    /** 同步状态：待处理 */
    public static final int SYNC_STATUS_PENDING = 0;

    /** 同步状态：成功 */
    public static final int SYNC_STATUS_SUCCESS = 1;

    /** 同步状态：失败 */
    public static final int SYNC_STATUS_FAILED = 2;

    /** 来源类型：工作流实例 */
    public static final String SOURCE_WORKFLOW = "WORKFLOW";

    /** 来源类型：合同（业务系统主动推送快照） */
    public static final String SOURCE_CONTRACT = "CONTRACT";

    /** 来源类型：文档页面（M3 在线文档归档快照） */
    public static final String SOURCE_DOC_PAGE = "DOC_PAGE";

    /** 文件角色：原件 */
    public static final String FILE_ROLE_ORIGINAL = "ORIGINAL";

    /** 文件角色：附件 */
    public static final String FILE_ROLE_ATTACHMENT = "ATTACHMENT";

    /** 文件角色：快照 */
    public static final String FILE_ROLE_SNAPSHOT = "SNAPSHOT";

    /** 根节点父ID */
    public static final long ROOT_PARENT_ID = 0L;

    /** 根节点层级 */
    public static final int ROOT_LEVEL = 1;

    /** 门类树最大深度（防止脏数据成环导致无限递归） */
    public static final int MAX_TREE_DEPTH = 64;

    /** 档号年度下限 */
    public static final int MIN_YEAR = 1900;

    /** 档号年度上限 */
    public static final int MAX_YEAR = 9999;

    /** 流水号宽度下限 */
    public static final int MIN_SEQUENCE_WIDTH = 1;

    /** 流水号宽度上限 */
    public static final int MAX_SEQUENCE_WIDTH = 12;

    /** 门类编码格式（大写/小写字母、数字、下划线） */
    public static final String CATEGORY_CODE_PATTERN = "^[A-Za-z0-9_]+$";

    /** 流程定义编码格式（字母、数字、下划线、点、连字符） */
    public static final String DEFINITION_CODE_PATTERN = "^[A-Za-z0-9_.-]+$";

    /** 表单字段类型：附件 */
    public static final String FORM_TYPE_ATTACHMENT = "attachment";

    /** 表单字段类型：子表 */
    public static final String FORM_TYPE_SUBTABLE = "subtable";

    /** 自动归档开关：开启 */
    public static final int AUTO_ARCHIVE_ON = 1;

    /** 工作流同步游标名称 */
    public static final String SYNC_CURSOR_WORKFLOW_APPROVED = "workflow_approved";

    /** 失败原因最大长度（与 archive_source_record.last_error 对齐） */
    public static final int MAX_ERROR_LENGTH = 1000;

    /** 快照文件后缀 */
    public static final String SNAPSHOT_FILE_SUFFIX = ".json";

    /** 快照 MIME 类型 */
    public static final String SNAPSHOT_CONTENT_TYPE = "application/json";

    /** 文件状态：归档锁定（与 whale-file file_info.status 对齐） */
    public static final int FILE_STATUS_LOCKED = 2;

    /** 文件锁定补扫单批上限 */
    public static final int MAX_LOCK_RECONCILE_LIMIT = 500;

    /** 工具类不允许实例化 */
    private ArchiveConstants() {
    }
}
