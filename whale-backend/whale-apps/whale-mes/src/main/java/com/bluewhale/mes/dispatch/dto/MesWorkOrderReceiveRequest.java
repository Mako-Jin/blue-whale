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
    @NotBlank(message = "ERP 生产订单号不能为空")
    @Size(max = 64, message = "ERP 生产订单号长度不能超过 64")
    private String erpOrderNo;

    /** 物料编码 */
    @NotBlank(message = "物料编码不能为空")
    @Size(max = 64, message = "物料编码长度不能超过 64")
    private String itemCode;

    /** 物料名称 */
    @Size(max = 128, message = "物料名称长度不能超过 128")
    private String itemName;

    /** 计划数量 */
    @NotNull(message = "计划数量不能为空")
    @DecimalMin(value = "0.0001", message = "计划数量必须大于 0")
    private BigDecimal plannedQuantity;

    /** 计量单位 */
    @Size(max = 16, message = "计量单位长度不能超过 16")
    private String uom;

    /** 工艺路线版本 */
    @Size(max = 32, message = "工艺路线版本长度不能超过 32")
    private String routingVersion;

    /** 优先级：NORMAL / URGENT */
    @Size(max = 16, message = "优先级长度不能超过 16")
    private String priority;

    /** 计划开工时间 */
    private LocalDateTime plannedStart;

    /** 计划完工时间 */
    private LocalDateTime plannedEnd;

    /** 工单编码，为空时按 ERP 单号生成 */
    @Size(max = 64, message = "工单编码长度不能超过 64")
    private String code;

    /** 工单名称，为空时取物料名称 */
    @Size(max = 128, message = "工单名称长度不能超过 128")
    private String name;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
