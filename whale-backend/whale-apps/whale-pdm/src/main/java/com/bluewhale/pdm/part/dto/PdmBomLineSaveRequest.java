package com.bluewhale.pdm.part.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * BOM 行新增/更新请求
 *
 * @author BlueWhale
 */
@Data
public class PdmBomLineSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 父件ID（根产品或其子件） */
    @NotNull(message = "{validation.pdm.pdm-bom-line.parent-part-id-required}")
    private Long parentPartId;

    /** 子件ID */
    @NotNull(message = "{validation.pdm.pdm-bom-line.child-part-id-required}")
    private Long childPartId;

    /** 同级行序号，缺省自动追加 */
    private Integer lineNo;

    /** 用量 */
    @NotNull(message = "{validation.pdm.pdm-bom-line.qty-required}")
    @DecimalMin(value = "0.000001", message = "{validation.pdm.pdm-bom-line.qty-min}")
    private BigDecimal qty;

    /** 单位，缺省取子件单位 */
    @Size(max = 16, message = "{validation.pdm.pdm-bom-line.uom-size}")
    private String uom;

    /** 损耗率（0-100） */
    @DecimalMin(value = "0", message = "{validation.pdm.pdm-bom-line.loss-rate-min}")
    private BigDecimal lossRate;

    /** 备注 */
    @Size(max = 255, message = "{validation.pdm.pdm-bom-line.remark-size}")
    private String remark;
}
