package com.bluewhale.pdm.document.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PDM 图文档版本
 *
 * <p>同一文档下版本号唯一；发布后文件、校验和与元数据不可再改，
 * 回滚通过以旧版本为基线新建草稿实现。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_doc_version")
public class PdmDocVersion extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 文档ID */
    private Long documentId;

    /** 版本号，如 V0.1 / V1.0 / V1.1 */
    private String versionNo;

    /** 版本排序值 */
    private Integer versionSeq;

    /** 状态：DRAFT/RELEASED/OBSOLETE */
    private String status;

    /** whale-file 文件ID */
    private Long fileId;

    /** 文件名 */
    private String fileName;

    /** 文件大小 */
    private Long fileSize;

    /** 内容类型 */
    private String contentType;

    /** SHA-256 校验和 */
    private String checksum;

    /** 变更说明 */
    private String changeNote;

    /** 来源：UPLOAD/CHECKIN/ROLLBACK */
    private String source;

    /** 创建人 */
    private Long createdBy;

    /** 发布人 */
    private Long releasedBy;

    /** 发布时间 */
    private LocalDateTime releasedAt;
}
