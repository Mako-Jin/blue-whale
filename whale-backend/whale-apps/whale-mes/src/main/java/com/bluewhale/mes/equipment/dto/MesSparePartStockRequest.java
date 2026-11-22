package com.bluewhale.mes.equipment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 备件出入库请求
 *
 * @author BlueWhale
 */
@Data
public class MesSparePartStockRequest {

    /** 备件编码 */
    @NotBlank(message = "{validation.mes.mes-spare-part-stock.part-code-required}")
    @Size(max = 64, message = "{validation.mes.mes-spare-part-stock.part-code-size}")
    private String partCode;

    /** 仓库编码（空则 DEFAULT） */
    @Size(max = 64, message = "{validation.mes.mes-spare-part-stock.warehouse-code-size}")
    private String warehouseCode;

    /** 数量（正数） */
    @NotNull(message = "{validation.mes.mes-spare-part-stock.quantity-required}")
    @DecimalMin(value = "0.0001", message = "{validation.mes.mes-spare-part-stock.quantity-min}")
    private BigDecimal quantity;

    /** 关联单号（维修工单/采购单） */
    @Size(max = 64, message = "{validation.mes.mes-spare-part-stock.ref-no-size}")
    private String refNo;

    /** 操作人 */
    @Size(max = 64, message = "{validation.mes.mes-spare-part-stock.operator-size}")
    private String operator;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-spare-part-stock.remark-size}")
    private String remark;
}
