package com.bluewhale.doc.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 页面标签关联实体，对应表 {@code doc_page_tag}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("doc_page_tag")
public class DocPageTag extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 页面ID */
    private Long pageId;

    /** 标签ID */
    private Long tagId;
}
