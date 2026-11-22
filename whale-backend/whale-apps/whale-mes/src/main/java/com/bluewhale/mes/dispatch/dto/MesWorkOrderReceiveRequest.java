package com.bluewhale.mes.dispatch.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ERP 生产工单下发请求（内网接口 {@code POST /mes/internal/work-order}）。
 *
 * <p>以 {@code erpOrderNo} 为幂等键：重复下发同一 ERP 生产订单号不重复建单；
 * 已派工的工单拒绝修改计划量。</p>
 *
 * @author BlueWhale
 */
@Data
public class MesWorkOrderReceiveRequest {

    /** ERP 生产订单号（幂等键） */
    @NotBlank(message = "{validation.mes.mes-work-order-receive.erp-order-no-required}")
    @Size(max = 64, message = "{validation.mes.mes-work-order-receive.erp-order-no-size}")
    private String erpOrderNo;

    /** 物料编码 */
    @NotBlank(message = "{validation.mes.mes-work-order-receive.item-code-required}")
    @Size(max = 64, message = "{validation.mes.mes-work-order-receive.item-code-size}")
    private String itemCode;

    /** 物料名称 */
    @Size(max = 128, message = "{validation.mes.mes-work-order-receive.item-name-size}")
    private String itemName;

    /** 计划数量 */
    @NotNull(message = "{validation.mes.mes-work-order-receive.planned-quantity-required}")
    @DecimalMin(value = "0.0001", message = "{validation.mes.mes-work-order-receive.planned-quantity-min}")
    private BigDecimal plannedQuantity;

    /** 计量单位 */
    @Size(max = 16, message = "{validation.mes.mes-work-order-receive.uom-size}")
    private String uom;

    /** 工艺路线版本 */
    @Size(max = 32, message = "{validation.mes.mes-work-order-receive.routing-version-size}")
    private String routingVersion;

    /** 优先级：NORMAL / URGENT */
    @Size(max = 16, message = "{validation.mes.mes-work-order-receive.priority-size}")
    private String priority;

    /** 计划开工时间 */
    private LocalDateTime plannedStart;

    /** 计划完工时间 */
    private LocalDateTime plannedEnd;

    /** 工单编码，为空时按 ERP 单号生成 */
    @Size(max = 64, message = "{validation.mes.mes-work-order-receive.code-size}")
    private String code;

    /** 工单名称，为空时取物料名称 */
    @Size(max = 128, message = "{validation.mes.mes-work-order-receive.name-size}")
    private String name;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-work-order-receive.remark-size}")
    private String remark;
}
