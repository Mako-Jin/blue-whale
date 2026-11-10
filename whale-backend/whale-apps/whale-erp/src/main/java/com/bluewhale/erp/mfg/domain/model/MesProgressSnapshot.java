package com.bluewhale.erp.mfg.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * MES 工单进度快照。
 *
 * @author BlueWhale
 */
@Data
public class MesProgressSnapshot {

    /** ERP 生产订单号 */
    private String erpOrderNo;

    /** ERP 生产订单 ID */
    private Long orderId;

    /** MES 工单 ID */
    private Long workOrderId;

    /** MES 工单编码 */
    private String workOrderCode;

    /** MES 工单状态 */
    private String mesStatus;

    /** 计划数量 */
    private BigDecimal plannedQuantity;

    /** 累计报工数量 */
    private BigDecimal reportedQuantity;

    /** 累计合格数量 */
    private BigDecimal qualifiedQuantity;

    /** 累计完工数量 */
    private BigDecimal completedQuantity;

    /** 任务明细 */
    private List<Task> tasks;

    /** 最近事件幂等键 */
    private String eventId;

    /** 接收时间 */
    private LocalDateTime receivedAt;

    /**
     * MES 任务明细快照。
     */
    @Data
    public static class Task {

        /** 任务号 */
        private String taskNo;

        /** 工序序号 */
        private Integer seqNo;

        /** 工序编码 */
        private String operationCode;

        /** 计划数量 */
        private BigDecimal plannedQuantity;

        /** 累计报工数量 */
        private BigDecimal reportedQuantity;

        /** 累计合格数量 */
        private BigDecimal qualifiedQuantity;

        /** 累计完工数量 */
        private BigDecimal completedQuantity;

        /** 任务状态 */
        private String status;
    }
}
