package com.bluewhale.erp.mfg.application.command;

import com.bluewhale.erp.mfg.domain.model.MesProgressSnapshot;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * MES 进度回传报文（payload 部分）。
 *
 * @author BlueWhale
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MesProgressCallback {

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
    private List<MesProgressSnapshot.Task> tasks;
}
