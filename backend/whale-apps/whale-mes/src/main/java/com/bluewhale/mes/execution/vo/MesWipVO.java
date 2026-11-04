package com.bluewhale.mes.execution.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 在制品视图对象
 *
 * @author BlueWhale
 */
@Data
public class MesWipVO {

    /** 任务 ID */
    private Long taskId;

    /** 任务编号 */
    private String taskNo;

    /** 工单 ID */
    private Long workOrderId;

    /** 工序编码 */
    private String operationCode;

    /** 已派工数量 */
    private BigDecimal dispatchedQuantity;

    /** 已完工数量 */
    private BigDecimal completedQuantity;

    /** 已报废数量 */
    private BigDecimal scrappedQuantity;

    /** 在制品数量 */
    private BigDecimal wipQuantity;

    /** 快照时间 */
    private LocalDateTime snapshotTime;
}
