package com.bluewhale.mes.dispatch.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 工单进度内网视图（供 ERP 对账拉取）。
 *
 * @author BlueWhale
 */
@Data
public class MesWorkOrderProgressVO {

    /** ERP 生产订单号 */
    private String erpOrderNo;

    /** MES 工单 ID */
    private Long workOrderId;

    /** MES 工单编码 */
    private String workOrderCode;

    /** MES 工单状态 */
    private String status;

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

    /**
     * 任务进度明细。
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
