package com.bluewhale.mes.execution.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 报工记录视图对象
 *
 * @author BlueWhale
 */
@Data
public class MesProductionReportVO {

    /** 主键 ID */
    private Long id;

    /** 报工单号 */
    private String reportNo;

    /** 任务 ID */
    private Long taskId;

    /** 任务编号 */
    private String taskNo;

    /** 工单 ID */
    private Long workOrderId;

    /** 工单编码 */
    private String workOrderCode;

    /** 工序编码 */
    private String operationCode;

    /** 工位 ID */
    private Long stationId;

    /** 工位编码 */
    private String stationCode;

    /** 报工数量 */
    private BigDecimal reportQuantity;

    /** 合格数量 */
    private BigDecimal qualifiedQuantity;

    /** 不良数量 */
    private BigDecimal defectQuantity;

    /** 状态 */
    private String status;

    /** 报工人 */
    private String reporter;

    /** 报工时间 */
    private LocalDateTime reportedAt;

    /** 备注 */
    private String remark;
}
