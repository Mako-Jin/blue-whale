package com.bluewhale.oa.document.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 公文归档映射实体，对应表 {@code oa_doc_archive_ref}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_doc_archive_ref")
public class DocArchiveRefEntity extends BaseEntity {

    private String docType;

    private Long docId;

    private String sourceType;

    private String sourceId;

    private String archiveItemId;

    private String status;

    private String errorMessage;

    private LocalDateTime pushedAt;
}
