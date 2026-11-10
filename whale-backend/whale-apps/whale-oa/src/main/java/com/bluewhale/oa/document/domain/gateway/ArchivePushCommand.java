package com.bluewhale.oa.document.domain.gateway;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公文归档推送命令（对齐 whale-archive 内网入档契约）。
 *
 * @author BlueWhale
 */
@Data
public class ArchivePushCommand {

    /** 来源类型（固定 OA_DOC） */
    private String sourceType;

    /** 来源业务ID，如 OUT-123 */
    private String sourceId;

    /** 档案标题 */
    private String title;

    /** 档案摘要 */
    private String summary;

    /** 关键词（逗号分隔） */
    private String keywords;

    /** 密级：1–3 */
    private Integer securityLevel;

    /** 业务定义编码 */
    private String definitionCode;

    /** 业务单号 */
    private String businessKey;

    /** 发起人标识 */
    private String initiator;

    /** 业务终态时间 */
    private LocalDateTime finishedAt;

    /** 不可变快照 JSON */
    private String snapshotJson;

    /** 快照文件名 */
    private String fileName;

    /** 原件文件ID */
    private Long originalFileId;

    /** 原件文件名 */
    private String originalFileName;

    /** 原件 MIME 类型 */
    private String originalContentType;

    /** 原件字节数 */
    private Long originalFileSize;
}
