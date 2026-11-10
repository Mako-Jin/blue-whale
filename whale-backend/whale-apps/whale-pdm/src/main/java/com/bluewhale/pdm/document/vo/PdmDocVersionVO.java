package com.bluewhale.pdm.document.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图文档版本视图
 *
 * @author BlueWhale
 */
@Data
public class PdmDocVersionVO {

    /** 版本ID */
    private Long id;

    /** 文档ID */
    private Long documentId;

    /** 版本号 */
    private String versionNo;

    /** 状态 */
    private String status;

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

    /** 来源 */
    private String source;

    /** 创建人 */
    private Long createdBy;

    /** 发布人 */
    private Long releasedBy;

    /** 发布时间 */
    private LocalDateTime releasedAt;

    /** 创建时间 */
    private LocalDateTime createTime;
}
