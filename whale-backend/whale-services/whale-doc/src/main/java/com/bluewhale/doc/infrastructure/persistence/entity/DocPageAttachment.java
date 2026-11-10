package com.bluewhale.doc.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 页面附件引用实体，对应表 {@code doc_page_attachment}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("doc_page_attachment")
public class DocPageAttachment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 页面ID */
    private Long pageId;

    /** whale-file 文件ID */
    private Long fileId;

    /** 原始文件名 */
    private String originalName;

    /** MIME 类型 */
    private String contentType;

    /** 文件字节数 */
    private Long fileSize;

    /** 用途：ATTACHMENT / INLINE */
    private String usageType;

    /** 排序 */
    private Integer sortNo;
}
