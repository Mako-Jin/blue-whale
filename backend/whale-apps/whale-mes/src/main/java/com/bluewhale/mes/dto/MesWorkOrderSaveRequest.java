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
    @NotBlank(message = "工单编码不能为空")
    @Size(max = 64, message = "工单编码长度不能超过 64")
    private String code;

    /** 工单名称 */
    @NotBlank(message = "工单名称不能为空")
    @Size(max = 128, message = "工单名称长度不能超过 128")
    private String name;

    /** 状态，新增时为空默认 RECEIVED */
    @Pattern(regexp = "RECEIVED|DISPATCHED|IN_PROGRESS|PAUSED|COMPLETED|CLOSED|CANCELLED",
            message = "状态仅支持工单生命周期取值")
    private String status;

    /** ERP 生产订单号（租户内唯一，可为空） */
    @Size(max = 64, message = "ERP 生产订单号长度不能超过 64")
    private String erpOrderNo;

    /** 物料编码 */
    @Size(max = 64, message = "物料编码长度不能超过 64")
    private String itemCode;

    /** 物料名称 */
    @Size(max = 128, message = "物料名称长度不能超过 128")
    private String itemName;

    /** 计划数量 */
    @DecimalMin(value = "0.0001", message = "计划数量必须大于 0")
    private BigDecimal plannedQuantity;

    /** 计量单位 */
    @Size(max = 16, message = "计量单位长度不能超过 16")
    private String uom;

    /** 工艺路线版本快照 */
    @Size(max = 32, message = "工艺路线版本长度不能超过 32")
    private String routingVersion;

    /** 优先级：NORMAL / URGENT */
    @Pattern(regexp = "NORMAL|URGENT", message = "优先级仅支持 NORMAL / URGENT")
    private String priority;

    /** 计划开工时间 */
    private LocalDateTime plannedStart;

    /** 计划完工时间 */
    private LocalDateTime plannedEnd;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
