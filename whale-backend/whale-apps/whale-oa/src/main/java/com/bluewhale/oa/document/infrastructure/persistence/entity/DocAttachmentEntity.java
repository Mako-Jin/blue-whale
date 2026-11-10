package com.bluewhale.oa.document.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 公文附件实体，对应表 {@code oa_doc_attachment}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_doc_attachment")
public class DocAttachmentEntity extends BaseEntity {

    private String docType;

    private Long docId;

    private Long fileId;

    private String fileName;

    private Long fileSize;

    private String contentType;

    private Integer sortNo;
}
