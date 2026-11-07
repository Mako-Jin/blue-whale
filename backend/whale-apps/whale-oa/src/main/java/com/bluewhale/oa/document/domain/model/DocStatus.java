package com.bluewhale.oa.document.domain.model;

/**
 * 公文模块状态与类型常量。
 *
 * <p>发文状态机：{@code DRAFT → PENDING → APPROVED → SIGNED → ISSUED → ARCHIVED}，
 * 审核驳回进入 {@code REJECTED}；收文状态机：
 * {@code REGISTERED → REVIEWED → FORWARDED → COMPLETED → ARCHIVED}。</p>
 *
 * @author BlueWhale
 */
public final class DocStatus {

    /** 单据类型：发文 */
    public static final String TYPE_OUTGOING = "OUTGOING";

    /** 单据类型：收文 */
    public static final String TYPE_INCOMING = "INCOMING";

    /** 模板状态：草稿 */
    public static final String TEMPLATE_DRAFT = "DRAFT";

    /** 模板状态：已发布 */
    public static final String TEMPLATE_RELEASED = "RELEASED";

    /** 模板状态：已停用 */
    public static final String TEMPLATE_DISABLED = "DISABLED";

    /** 发文状态：草稿 */
    public static final String OUTGOING_DRAFT = "DRAFT";

    /** 发文状态：审核中 */
    public static final String OUTGOING_PENDING = "PENDING";

    /** 发文状态：审核通过 */
    public static final String OUTGOING_APPROVED = "APPROVED";

    /** 发文状态：审核驳回 */
    public static final String OUTGOING_REJECTED = "REJECTED";

    /** 发文状态：已签发 */
    public static final String OUTGOING_SIGNED = "SIGNED";

    /** 发文状态：已下发 */
    public static final String OUTGOING_ISSUED = "ISSUED";

    /** 发文状态：已归档 */
    public static final String OUTGOING_ARCHIVED = "ARCHIVED";

    /** 收文状态：已登记 */
    public static final String INCOMING_REGISTERED = "REGISTERED";

    /** 收文状态：已批阅 */
    public static final String INCOMING_REVIEWED = "REVIEWED";

    /** 收文状态：已转发 */
    public static final String INCOMING_FORWARDED = "FORWARDED";

    /** 收文状态：已办结 */
    public static final String INCOMING_COMPLETED = "COMPLETED";

    /** 收文状态：已归档 */
    public static final String INCOMING_ARCHIVED = "ARCHIVED";

    /** 归档映射状态：待推送 */
    public static final String ARCHIVE_PENDING = "PENDING";

    /** 归档映射状态：已归档 */
    public static final String ARCHIVE_ARCHIVED = "ARCHIVED";

    /** 归档映射状态：失败 */
    public static final String ARCHIVE_FAILED = "FAILED";

    /** 档案来源类型（whale-archive 白名单） */
    public static final String ARCHIVE_SOURCE_TYPE = "OA_DOC";

    /** 文号期间格式：按年 */
    public static final String PERIOD_YEAR = "YEAR";

    /** 文号期间格式：按月 */
    public static final String PERIOD_MONTH = "MONTH";

    /** 文号期间格式：无期间 */
    public static final String PERIOD_NONE = "NONE";

    /** 工具类不允许实例化 */
    private DocStatus() {
    }
}
