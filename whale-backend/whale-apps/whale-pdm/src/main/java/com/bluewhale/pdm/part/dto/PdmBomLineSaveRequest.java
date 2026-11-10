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
    @NotNull(message = "父件不能为空")
    private Long parentPartId;

    /** 子件ID */
    @NotNull(message = "子件不能为空")
    private Long childPartId;

    /** 同级行序号，缺省自动追加 */
    private Integer lineNo;

    /** 用量 */
    @NotNull(message = "用量不能为空")
    @DecimalMin(value = "0.000001", message = "用量必须大于 0")
    private BigDecimal qty;

    /** 单位，缺省取子件单位 */
    @Size(max = 16, message = "单位长度不能超过 16")
    private String uom;

    /** 损耗率（0-100） */
    @DecimalMin(value = "0", message = "损耗率不能小于 0")
    private BigDecimal lossRate;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
