package com.bluewhale.archive.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 归档文件锁定补扫结果。
 *
 * <p>用于修复历史已归档条目中未锁定的快照/原件/附件；按条目 ID 游标分批执行。</p>
 *
 * @author BlueWhale
 */
@Data
public class ArchiveFileLockReconcileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 本批扫描的已归档条目数 */
    private Integer scannedItems;

    /** 本批成功补锁的文件数 */
    private Integer lockedFiles;

    /** 本批已处于锁定状态而跳过的文件数 */
    private Integer alreadyLockedFiles;

    /** 本批文件记录存在但文件服务已不存在的数量 */
    private Integer missingFiles;

    /** 本批锁定失败的文件数 */
    private Integer failedFiles;

    /** 下一批游标（本批最后一条条目ID；无条目时沿用请求游标） */
    private Long nextAfterId;

    /** 是否还有下一批 */
    private Boolean hasMore;
}
