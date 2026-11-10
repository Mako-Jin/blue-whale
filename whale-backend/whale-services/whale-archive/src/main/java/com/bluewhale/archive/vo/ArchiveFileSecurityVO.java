package com.bluewhale.archive.vo;

import lombok.Data;

/**
 * 档案文件密级视图：文件 → 所属条目 → 密级（知识库过滤用）。
 *
 * @author BlueWhale
 */
@Data
public class ArchiveFileSecurityVO {

    /** 文件ID（whale-file 的 file_info.id） */
    private Long fileId;

    /** 档案条目ID */
    private Long itemId;

    /** 密级：1 公开 / 2 内部 / 3 秘密 */
    private Integer securityLevel;
}
