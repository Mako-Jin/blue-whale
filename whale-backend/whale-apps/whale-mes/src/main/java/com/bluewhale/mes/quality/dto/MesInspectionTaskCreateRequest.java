package com.bluewhale.mes.quality.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 检验任务创建请求
 *
 * @author BlueWhale
 */
@Data
public class MesInspectionTaskCreateRequest {

    /** 任务类型：FIRST_ARTICLE / IPQC */
    @NotBlank(message = "{validation.mes.mes-inspection-task.task-type-required}")
    @Pattern(regexp = "FIRST_ARTICLE|IPQC", message = "{validation.mes.mes-inspection-task.task-type-pattern}")
    private String taskType;

    /** 检验计划 ID（空则按类型 + 工序匹配启用计划） */
    private Long planId;

    /** 工单 ID */
    private Long workOrderId;

    /** 工单编码 */
    @Size(max = 64, message = "{validation.mes.mes-inspection-task.work-order-code-size}")
    private String workOrderCode;

    /** 工单工序任务 ID */
    private Long execTaskId;

    /** 工单工序任务号 */
    @Size(max = 64, message = "{validation.mes.mes-inspection-task.exec-task-no-size}")
    private String execTaskNo;

    /** 工序编码 */
    @Size(max = 64, message = "{validation.mes.mes-inspection-task.operation-code-size}")
    private String operationCode;

    /** 产线 ID */
    private Long lineId;

    /** 产线编码 */
    @Size(max = 64, message = "{validation.mes.mes-inspection-task.line-code-size}")
    private String lineCode;

    /** 工位编码 */
    @Size(max = 64, message = "{validation.mes.mes-inspection-task.station-code-size}")
    private String stationCode;

    /** 检验批次号 */
    @Size(max = 64, message = "{validation.mes.mes-inspection-task.batch-no-size}")
    private String batchNo;

    /** 应检样本数（空则取计划抽样数量） */
    private Integer sampleSize;

    /** 检验员 */
    @Size(max = 64, message = "{validation.mes.mes-inspection-task.inspector-size}")
    private String inspector;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-inspection-task.remark-size}")
    private String remark;
}
