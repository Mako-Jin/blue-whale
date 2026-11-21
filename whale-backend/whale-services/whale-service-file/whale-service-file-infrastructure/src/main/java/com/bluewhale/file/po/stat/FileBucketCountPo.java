package com.bluewhale.file.po.stat;

import lombok.Data;

/**
 * 按桶聚合行（藏书殿概览 T5.4）。
 *
 * @author BlueWhale
 */
@Data
public class FileBucketCountPo {

    /** 桶名称；允许为空（历史数据可能没写桶） */
    private String bucket;

    /** 根文件数 */
    private Long files;
}
