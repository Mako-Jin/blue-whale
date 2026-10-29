package com.bluewhale.doc.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 页面引用关系实体，对应表 {@code doc_page_link}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("doc_page_link")
public class DocPageLink extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 引用方页面ID */
    private Long sourcePageId;

    /** 被引用页面ID */
    private Long targetPageId;

    /** 链接文本/锚点摘要 */
    private String linkText;
}
