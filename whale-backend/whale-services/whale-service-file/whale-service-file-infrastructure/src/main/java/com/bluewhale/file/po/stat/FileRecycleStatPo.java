package com.bluewhale.file.po.stat;

import lombok.Data;

/**
 * 回收站聚合行（藏书殿概览 T5.4）。
 *
 * <p>{@code staleFiles} 用同一个 {@code WHERE} 里的条件求和得出（而不是再查一遍），
 * 保证"回收站总数"与"其中超期"永远能对上。</p>
 *
 * @author BlueWhale
 */
@Data
public class FileRecycleStatPo {

    /** 回收站根文件数（{@code deleted = 1 AND parent_id IS NULL}） */
    private Long recycleFiles;

    /** 回收站占用字节合计 */
    private Long recycleBytes;

    /** 已过保留期仍未清理的根文件数 */
    private Long staleFiles;
}
