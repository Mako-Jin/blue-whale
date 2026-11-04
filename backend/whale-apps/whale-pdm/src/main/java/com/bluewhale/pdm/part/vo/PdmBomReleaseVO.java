package com.bluewhale.pdm.part.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * BOM 发布记录视图
 *
 * @author BlueWhale
 */
@Data
public class PdmBomReleaseVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** BOM 编号 */
    private String bomNo;

    /** 版本号 */
    private String versionNo;

    /** 产品编码 */
    private String productCode;

    /** 视图 */
    private String view;

    /** 快照校验和 */
    private String checksum;

    /** 快照行数 */
    private Integer lineCount;

    /** 发布人 */
    private Long releasedBy;

    /** 发布时间 */
    private LocalDateTime releasedAt;

    /** 创建时间 */
    private LocalDateTime createTime;
}
