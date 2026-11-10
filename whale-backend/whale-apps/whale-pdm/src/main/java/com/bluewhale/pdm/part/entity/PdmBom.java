package com.bluewhale.pdm.part.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PDM BOM 头
 *
 * <p>一个 {@code bomNo} 下允许多个版本，每版本一行；{@code view} 支持 EBOM/PBOM/MBOM，
 * PBOM/MBOM 由 EBOM 派生并独立维护。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_bom")
public class PdmBom extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** BOM 编号（租户内业务键，与版本组合唯一） */
    private String bomNo;

    /** 成品/部件零件ID */
    private Long productId;

    /** 产品编码快照 */
    private String productCode;

    /** 视图：EBOM/PBOM/MBOM */
    private String view;

    /** 版本号，如 V1.0 */
    private String versionNo;

    /** 版本排序值，major*10000+minor */
    private Integer versionSeq;

    /** 状态：DRAFT/RELEASED/OBSOLETE */
    private String status;

    /** 派生来源 BOM ID（原生视图为空） */
    private Long sourceBomId;

    /** 派生来源版本号（原生视图为空） */
    private String sourceVersionNo;

    /** 备注 */
    private String remark;
}
