package com.bluewhale.doc.domain.model;

/**
 * 文档页面状态。
 *
 * <p>M1 使用 {@link #DRAFT} / {@link #PUBLISHED} / {@link #ARCHIVED}；
 * {@link #PENDING_APPROVAL} 为 M3 发布审批开关启用后的可选状态。</p>
 *
 * @author BlueWhale
 */
public enum DocPageStatus {

    /** 草稿：仅空间成员可见 */
    DRAFT,

    /** 已发布：公开空间内租户成员可见，可被知识源索引 */
    PUBLISHED,

    /** 已归档：只读，不参与公开搜索 */
    ARCHIVED,

    /** 待审批：M3 发布审批 */
    PENDING_APPROVAL
}
