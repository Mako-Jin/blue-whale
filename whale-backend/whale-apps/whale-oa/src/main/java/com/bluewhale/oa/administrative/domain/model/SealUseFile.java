package com.bluewhale.oa.administrative.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用章文件。
 *
 * @author BlueWhale
 */
@Data
public class SealUseFile {

    /** 主键ID */
    private Long id;

    /** 用章申请ID */
    private Long sealUseId;

    /** 文件ID */
    private Long fileId;

    /** 文件名 */
    private String fileName;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
