package com.bluewhale.archive.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 业务系统快照归档请求（内网推送）。
 *
 * <p>由业务服务（如 whale-contract）在业务单据终态时主动推送：档案服务负责落快照文件、
 * 计算 SHA-256 并生成「待整理」档案条目，档号在档案人员确认归档时统一分配。
 * {@code (sourceType, sourceId)} 为幂等键，重复推送返回既有条目。</p>
 *
 * @author BlueWhale
 */
@Data
public class ArchiveIngestRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 来源类型：CONTRACT 合同 / DOC_PAGE 文档页面（白名单校验） */
    @NotBlank(message = "来源类型不能为空")
    @Size(max = 32, message = "来源类型不能超过 32 字")
    private String sourceType;

    /** 来源业务ID */
    @NotBlank(message = "来源ID不能为空")
    @Size(max = 64, message = "来源ID不能超过 64 字")
    private String sourceId;

    /** 档案标题 */
    @NotBlank(message = "档案标题不能为空")
    @Size(max = 255, message = "档案标题不能超过 255 字")
    private String title;

    /** 档案摘要 */
    @Size(max = 2000, message = "档案摘要不能超过 2000 字")
    private String summary;

    /** 关键词（逗号分隔） */
    @Size(max = 512, message = "关键词不能超过 512 字")
    private String keywords;

    /** 密级：1 公开 / 2 内部 / 3 秘密（缺省为内部） */
    @Min(value = 1, message = "密级取值 1-3")
    @Max(value = 3, message = "密级取值 1-3")
    private Integer securityLevel;

    /** 流程/业务定义编码（如 CONTRACT） */
    @Size(max = 64, message = "定义编码不能超过 64 字")
    private String definitionCode;

    /** 业务单号 */
    @Size(max = 128, message = "业务单号不能超过 128 字")
    private String businessKey;

    /** 发起人标识（账号或用户ID字符串） */
    @Size(max = 64, message = "发起人不能超过 64 字")
    private String initiator;

    /** 业务终态时间（缺省取当前时间，用于归档年度） */
    private LocalDateTime finishedAt;

    /** 不可变快照内容（JSON 文本，不超过 1MB） */
    @NotBlank(message = "快照内容不能为空")
    private String snapshotJson;

    /** 快照文件名（缺省按来源类型与ID生成） */
    @Size(max = 255, message = "快照文件名不能超过 255 字")
    private String fileName;

    /** 原件文件ID（whale-file，可选） */
    private Long originalFileId;

    /** 原件文件名 */
    @Size(max = 255, message = "原件文件名不能超过 255 字")
    private String originalFileName;

    /** 原件 MIME 类型 */
    @Size(max = 128, message = "原件 MIME 类型不能超过 128 字")
    private String originalContentType;

    /** 原件大小（字节） */
    private Long originalFileSize;
}
