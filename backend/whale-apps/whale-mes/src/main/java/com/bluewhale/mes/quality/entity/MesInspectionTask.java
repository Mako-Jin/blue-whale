package com.bluewhale.mes.quality.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 检验任务实体，对应表 {@code mes_qc_inspection_task}。
 *
 * <p>检验项未全部录入时不得判定；判定为 {@code FAILED} 时由服务层自动生成 NCR。
 * 首件任务最近一次判定结果作为批量释放门禁依据。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_qc_inspection_task")
public class MesInspectionTask extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 检验任务号（租户内唯一） */
    private String taskNo;

    /** 任务类型：FIRST_ARTICLE / IPQC */
    private String taskType;

    /** 检验计划 ID */
    private Long planId;

    /** 检验计划编号快照 */
    private String planNo;

    /** 工单 ID */
    private Long workOrderId;

    /** 工单编码快照 */
    private String workOrderCode;

    /** 工单工序任务 ID */
    private Long execTaskId;

    /** 工单工序任务号快照 */
    private String execTaskNo;

    /** 工序编码 */
    private String operationCode;

    /** 产线 ID */
    private Long lineId;

    /** 产线编码快照 */
    private String lineCode;

    /** 工位编码 */
    private String stationCode;

    /** 检验批次号 */
    private String batchNo;

    /** 应检样本数 */
    private Integer sampleSize;

    /** 已录入检验项数 */
    private Integer inspectedQuantity;

    /** 状态：PENDING / INSPECTED / PASSED / FAILED / CANCELLED */
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

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
