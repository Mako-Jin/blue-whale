package com.bluewhale.file.po.stat;

import lombok.Data;

/**
 * 库存总量聚合行（藏书殿概览 T5.4）。
 *
 * <p>根文件数与字节合计在同一行里返回：两次查询之间可能有上传落库，
 * 分开查会出现"37 个文件占了 36 个文件的盘"这种对不上的组合。</p>
 *
 * @author BlueWhale
 */
@Data
public class FileLibraryStatPo {

    /** 根文件数（{@code parent_id IS NULL}，与文件列表条数同口径） */
    private Long rootFiles;

    /** 占用字节合计（含各版本行） */
    private Long totalBytes;
}
