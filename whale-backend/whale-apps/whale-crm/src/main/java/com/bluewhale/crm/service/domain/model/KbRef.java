package com.bluewhale.crm.service.domain.model;

import lombok.Data;

/**
 * 知识引用元数据（正文归 whale-doc，CRM 只存引用与命中）。
 *
 * @author BlueWhale
 */
@Data
public class KbRef {

    /** 主键 */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** FAQ 标题 */
    private String title;

    /** 分类 */
    private String category;

    /** 产品标签（逗号分隔） */
    private String productTags;

    /** 故障标签（逗号分隔） */
    private String faultTags;

    /** 可见范围：PUBLIC / INTERNAL */
    private String visibleScope;

    /** whale-doc 文档 ID */
    private Long docId;

    /** 文档版本 */
    private Integer docVersion;

    /** 文档是否可用 */
    private Boolean docAvailable;

    /** 引用命中次数 */
    private Integer hitCount;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /**
     * 标记文档可用性。
     *
     * @param available 是否可用
     */
    public void markDocAvailable(boolean available) {
        this.docAvailable = available;
    }

    /**
     * 记录一次引用命中。
     */
    public void recordHit() {
        this.hitCount = (this.hitCount == null ? 0 : this.hitCount) + 1;
    }
}
