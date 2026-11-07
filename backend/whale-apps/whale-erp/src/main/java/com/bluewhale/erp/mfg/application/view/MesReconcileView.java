package com.bluewhale.erp.mfg.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * MES 对账行视图。
 *
 * @author BlueWhale
 */
@Data
public class MesReconcileView {

    /** 生产订单 ID */
    private Long orderId;

    /** 生产订单号 */
    private String orderNo;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 计划数量 */
    private BigDecimal plannedQuantity;

    /** ERP 已完工数量 */
    private BigDecimal completedQuantity;

    /** ERP 订单状态 */
    private String status;

    /** 计划开工日期 */
    private LocalDate planStartDate;

    /** MES 下发状态 */
    private String mesSyncStatus;

    /** MES 工单编码 */
    private String mesWorkOrderCode;

    /** 最近成功下发时间 */
    private LocalDateTime mesSyncedAt;

    /** 最近一次下发/拉取失败摘要 */
    private String mesLastError;

    /** MES 工单状态 */
    private String mesStatus;

    /** MES 累计报工数量 */
    private BigDecimal mesReportedQuantity;

    /** MES 累计合格数量 */
    private BigDecimal mesQualifiedQuantity;

    /** MES 累计完工数量 */
    private BigDecimal mesCompletedQuantity;

    /** 进度快照接收时间 */
    private LocalDateTime mesReceivedAt;

    /** 完工数量差异标记 */
    private Boolean completedMismatch;

    /** 最近一次回调被拒标记 */
    private Boolean callbackRejected;

    /** 最近一次回调结果 */
    private String lastCallbackStatus;

    /** 最近一次回调错误 */
    private String lastCallbackError;
}
