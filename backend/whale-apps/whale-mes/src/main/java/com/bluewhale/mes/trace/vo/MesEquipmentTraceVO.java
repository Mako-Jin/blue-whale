package com.bluewhale.mes.trace.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备维度追溯视图。
 *
 * <p>以设备所在工位为纽带，串起该工位的历史报工与涉及批次，用于按设备反查质量影响范围。</p>
 *
 * @author BlueWhale
 */
@Data
public class MesEquipmentTraceVO {

    /** 设备编码 */
    private String equipmentCode;

    /** 设备名称 */
    private String equipmentName;

    /** 工作中心编码 */
    private String workCenterCode;

    /** 工位编码 */
    private String stationCode;

    /** 设备当前状态 */
    private String status;

    /** 该工位报工记录 */
    private List<Report> reports;

    /** 关联批次 */
    private List<Batch> batches;

    /**
     * 报工记录。
     */
    @Data
    public static class Report {

        /** 报工单号 */
        private String reportNo;

        /** 任务编号 */
        private String taskNo;

        /** 工单编码 */
        private String workOrderCode;

        /** 工序编码 */
        private String operationCode;

        /** 报工数量 */
        private BigDecimal reportQuantity;

        /** 合格数量 */
        private BigDecimal qualifiedQuantity;

        /** 不良数量 */
        private BigDecimal defectQuantity;

        /** 报工时间 */
        private LocalDateTime reportedAt;
    }

    /**
     * 关联批次。
     */
    @Data
    public static class Batch {

        /** 批次号 */
        private String batchNo;

        /** 物料编码 */
        private String itemCode;

        /** 物料名称 */
        private String itemName;

        /** 工单编码 */
        private String workOrderCode;

        /** 批次数量 */
        private BigDecimal quantity;

        /** 生产日期 */
        private LocalDate productionDate;

        /** 批次状态 */
        private String status;
    }
}
