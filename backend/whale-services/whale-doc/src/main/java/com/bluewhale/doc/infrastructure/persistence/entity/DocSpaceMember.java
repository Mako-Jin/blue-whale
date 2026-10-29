package com.bluewhale.doc.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 空间成员实体，对应表 {@code doc_space_member}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("doc_space_member")
public class DocSpaceMember extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 空间ID */
    private Long spaceId;

    /** 成员用户ID */
    private Long userId;

    /** 空间角色：OWNER / ADMIN / EDITOR / READER */
    private String memberRole;
}
