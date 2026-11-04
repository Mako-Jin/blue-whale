package com.bluewhale.mes.dispatch.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单拆分任务视图对象
 *
 * @author BlueWhale
 */
@Data
public class MesWorkOrderTaskVO {

    /** 主键 ID */
    private Long id;

    /** 任务编号 */
    private String taskNo;

    /** 工单 ID */
    private Long workOrderId;

    /** 工单编码 */
    private String workOrderCode;

    /** ERP 生产订单号 */
    private String erpOrderNo;

    /** 工序顺序 */
    private Integer seqNo;

    /** 工序编码 */
    private String operationCode;

    /** 工序名称 */
    private String operationName;

    /** 工作中心 ID */
    private Long workCenterId;

    /** 工作中心编码 */
    private String workCenterCode;

    /** 计划数量 */
    private BigDecimal plannedQuantity;

    /** 已派工数量 */
    private BigDecimal dispatchedQuantity;

    /** 累计报工数量 */
    private BigDecimal reportedQuantity;

    /** 累计合格数量 */
    private BigDecimal qualifiedQuantity;

    /** 累计不良数量 */
    private BigDecimal defectQuantity;

    /** 累计完工数量 */
    private BigDecimal completedQuantity;

    /** 累计报废数量 */
    private BigDecimal scrappedQuantity;

    /** 状态 */
    private String status;

    /** 优先级 */
    private String priority;

    /** 计划开工时间 */
    private LocalDateTime plannedStart;

    /** 计划完工时间 */
    private LocalDateTime plannedEnd;

    /** 备注 */
    private String remark;

    /** 乐观锁版本 */
    private Integer version;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
