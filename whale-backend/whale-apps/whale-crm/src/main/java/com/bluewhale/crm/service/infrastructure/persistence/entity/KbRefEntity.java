package com.bluewhale.crm.service.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识引用实体，对应表 {@code crm_kb_ref}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_kb_ref")
public class KbRefEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 标题 */
    private String title;

    /** 分类 */
    private String category;

    /** 产品标签 */
    private String productTags;

    /** 故障标签 */
    private String faultTags;

    /** 可见范围 */
    private String visibleScope;

    /** whale-doc 文档 ID */
    private Long docId;

    /** 文档版本 */
    private Integer docVersion;

    /** 文档是否可用 */
    private Boolean docAvailable;

    /** 命中次数 */
    private Integer hitCount;

    /** 状态 */
    private String status;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;
}
