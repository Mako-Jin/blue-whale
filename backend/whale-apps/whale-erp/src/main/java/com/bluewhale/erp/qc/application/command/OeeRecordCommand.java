package com.bluewhale.erp.qc.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * OEE 数据回传命令（MES 推送或人工录入）。
 *
 * @author BlueWhale
 */
@Data
public class OeeRecordCommand {

    /** 设备编码 */
    @NotBlank
    private String equipmentNo;

    /** 统计日期 */
    @NotNull
    private LocalDate recordDate;

    /** 计划运行分钟 */
    @NotNull
    private BigDecimal plannedMinutes;

    /** 实际运行分钟 */
    private BigDecimal runMinutes;

    /** 停机分钟 */
    private BigDecimal downMinutes;

    /** 理论产量 */
    @NotNull
    private BigDecimal idealQty;

    /** 实际产量 */
    private BigDecimal outputQty;

    /** 合格产量 */
    private BigDecimal goodQty;
}
