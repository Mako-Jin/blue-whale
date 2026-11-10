package com.bluewhale.mes.quality.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 检验任务视图对象
 *
 * @author BlueWhale
 */
@Data
public class MesInspectionTaskVO {

    /** 任务 ID */
    private Long id;

    /** 检验任务号 */
    private String taskNo;

    /** 任务类型 */
    private String taskType;

    /** 检验计划 ID */
    private Long planId;

    /** 检验计划编号 */
    private String planNo;

    /** 工单 ID */
    private Long workOrderId;

    /** 工单编码 */
    private String workOrderCode;

    /** 工序任务 ID */
    private Long execTaskId;

    /** 工序任务号 */
    private String execTaskNo;

    /** 工序编码 */
    private String operationCode;

    /** 产线 ID */
    private Long lineId;

    /** 产线编码 */
    private String lineCode;

    /** 工位编码 */
    private String stationCode;

    /** 批次号 */
    private String batchNo;

    /** 应检样本数 */
    private Integer sampleSize;

    /** 已录入检验项数 */
    private Integer inspectedQuantity;

    /** 状态 */
    private String status;

    /** 检验员 */
    private String inspector;

    /** 判定人 */
    private String judge;

    /** 判定时间 */
    private LocalDateTime judgedAt;

    /** 判定结论 */
    private String conclusion;

    /** 自动生成的 NCR ID */
    private Long ncrId;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
