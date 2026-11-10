package com.bluewhale.pdm.part.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PDM BOM 发布快照
 *
 * <p>发布后不可修改；{@code payload} 保存稳定 JSON，{@code checksum} 为其 SHA-256。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_bom_release")
public class PdmBomRelease extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** BOM 头ID */
    private Long bomId;

    /** BOM 编号 */
    private String bomNo;

    /** 版本号 */
    private String versionNo;

    /** 产品编码 */
    private String productCode;

    /** 视图 */
    private String view;

    /** 稳定 JSON 的 SHA-256 */
    private String checksum;

    /** 快照行数 */
    private Integer lineCount;

    /** 稳定 JSON 快照 */
    private String payload;

    /** 发布人 */
    private Long releasedBy;

    /** 发布时间 */
    private LocalDateTime releasedAt;
}
