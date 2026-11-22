package com.bluewhale.mes.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产工单新增 / 更新请求
 *
 * @author BlueWhale
 */
@Data
public class MesWorkOrderSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工单编码（租户内唯一） */
    @NotBlank(message = "{validation.mes.mes-work-order.code-required}")
    @Size(max = 64, message = "{validation.mes.mes-work-order.code-size}")
    private String code;

    /** 工单名称 */
    @NotBlank(message = "{validation.mes.mes-work-order.name-required}")
    @Size(max = 128, message = "{validation.mes.mes-work-order.name-size}")
    private String name;

    /** 状态，新增时为空默认 RECEIVED */
    @Pattern(regexp = "RECEIVED|DISPATCHED|IN_PROGRESS|PAUSED|COMPLETED|CLOSED|CANCELLED",
            message = "{validation.mes.mes-work-order.status-pattern}")
    private String status;

    /** ERP 生产订单号（租户内唯一，可为空） */
    @Size(max = 64, message = "{validation.mes.mes-work-order.erp-order-no-size}")
    private String erpOrderNo;

    /** 物料编码 */
    @Size(max = 64, message = "{validation.mes.mes-work-order.item-code-size}")
    private String itemCode;

    /** 物料名称 */
    @Size(max = 128, message = "{validation.mes.mes-work-order.item-name-size}")
    private String itemName;

    /** 计划数量 */
    @DecimalMin(value = "0.0001", message = "{validation.mes.mes-work-order.planned-quantity-min}")
    private BigDecimal plannedQuantity;

    /** 计量单位 */
    @Size(max = 16, message = "{validation.mes.mes-work-order.uom-size}")
    private String uom;

    /** 工艺路线版本快照 */
    @Size(max = 32, message = "{validation.mes.mes-work-order.routing-version-size}")
    private String routingVersion;

    /** 优先级：NORMAL / URGENT */
    @Pattern(regexp = "NORMAL|URGENT", message = "{validation.mes.mes-work-order.priority-pattern}")
    private String priority;

    /** 计划开工时间 */
    private LocalDateTime plannedStart;

    /** 计划完工时间 */
    private LocalDateTime plannedEnd;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-work-order.remark-size}")
    private String remark;
}
