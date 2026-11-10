package com.bluewhale.archive.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 档案条目视图（分页列表使用）。
 *
 * @author BlueWhale
 */
@Data
public class ArchiveItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 条目ID */
    private String id;

    /** 档号（待整理条目为空） */
    private String archiveNo;

    /** 档案标题 */
    private String title;

    /** 门类ID */
    private String categoryId;

    /** 门类名称（联表补全） */
    private String categoryName;

    /** 门类路径（编码以 / 连接） */
    private String categoryPath;

    /** 归档年度 */
    private Integer year;

    /** 保管期限（年；空表示长期） */
    private Integer retentionYears;

    /** 到期时间（长期为空） */
    private LocalDateTime expireAt;

    /** 密级：1 公开 / 2 内部 / 3 秘密 */
    private Integer securityLevel;

    /** 来源类型 */
    private String sourceType;

    /** 来源实例ID */
    private String sourceId;

    /** 流程定义编码 */
    private String definitionCode;

    /** 业务单号 */
    private String businessKey;

    /** 发起人 */
    private String initiator;

    /** 来源实例办结时间 */
    private LocalDateTime finishedAt;

    /** 条目状态：0 待整理 / 1 已归档 / 2 已销毁 */
    private Integer itemStatus;

    /** 档案摘要 */
    private String summary;

    /** 关键词（逗号分隔） */
    private String keywords;

    /** 不可变快照文件ID */
    private String snapshotFileId;

    /** 快照 SHA-256 摘要 */
    private String snapshotHash;

    /** 确认归档时间 */
    private LocalDateTime archivedAt;

    /** 创建时间 */
    private LocalDateTime createTime;
}
