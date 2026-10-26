package com.bluewhale.archive.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 业务系统快照归档结果。
 *
 * <p>ID 统一以字符串返回，避免前端/调用方 Long 精度丢失。</p>
 *
 * @author BlueWhale
 */
@Data
public class ArchiveIngestResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 档案条目ID */
    private String itemId;

    /** 来源类型 */
    private String sourceType;

    /** 来源业务ID */
    private String sourceId;

    /** 条目状态：0 待整理 / 1 已归档 / 2 已销毁 */
    private Integer itemStatus;

    /** 快照文件ID */
    private String snapshotFileId;

    /** 快照 SHA-256 摘要 */
    private String snapshotHash;

    /** 是否本次新建（幂等命中时为 {@code false}） */
    private Boolean created;
}
