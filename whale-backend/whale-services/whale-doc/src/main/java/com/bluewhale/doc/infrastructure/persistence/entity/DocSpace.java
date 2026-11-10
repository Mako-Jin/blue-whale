package com.bluewhale.doc.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文档空间实体，对应表 {@code doc_space}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("doc_space")
public class DocSpace extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 空间编码（租户内唯一，创建后不可改） */
    private String spaceKey;

    /** 空间名称 */
    private String name;

    /** 空间描述 */
    private String description;

    /** 图标标识 */
    private String icon;

    /** 可见性：PUBLIC / MEMBER */
    private String visibility;

    /** 空间所有者ID */
    private Long ownerId;

    /** 状态：1 正常 / 0 归档 */
    private Integer status;

    /** 我的空间排序 */
    private Integer sortNo;
}
