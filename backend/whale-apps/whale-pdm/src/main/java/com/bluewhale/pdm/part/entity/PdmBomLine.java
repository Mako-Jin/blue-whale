package com.bluewhale.pdm.part.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * PDM BOM 行（父件 → 子件边）
 *
 * <p>同一 BOM 同一父件下 {@code lineNo} 与 {@code childPartId} 均唯一。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_bom_line")
public class PdmBomLine extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** BOM 头ID */
    private Long bomId;

    /** 父件ID */
    private Long parentPartId;

    /** 子件ID */
    private Long childPartId;

    /** 同级行序号 */
    private Integer lineNo;

    /** 用量 */
    private BigDecimal qty;

    /** 单位 */
    private String uom;

    /** 损耗率（0-100） */
    private BigDecimal lossRate;

    /** 备注 */
    private String remark;
}
