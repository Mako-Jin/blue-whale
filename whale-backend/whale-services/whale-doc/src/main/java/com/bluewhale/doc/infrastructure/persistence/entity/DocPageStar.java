package com.bluewhale.doc.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 页面收藏实体，对应表 {@code doc_page_star}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("doc_page_star")
public class DocPageStar extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 收藏用户ID */
    private Long userId;

    /** 页面ID */
    private Long pageId;
}
