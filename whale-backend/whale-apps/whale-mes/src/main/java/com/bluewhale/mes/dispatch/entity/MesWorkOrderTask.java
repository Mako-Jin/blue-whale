package com.bluewhale.mes.dispatch.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单拆分任务实体，对应表 {@code mes_dp_work_order_task}。
 *
 * <p>任务按工艺路线工序拆分，一个任务对应工单的一个工序顺序；派工与报工都以任务为粒度。
 * 数量口径：已派工 ≤ 计划数量，累计报工 ≤ 已派工，完工数量为合格数量汇总。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_dp_work_order_task")
public class MesWorkOrderTask extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 任务编号（租户内唯一） */
    private String taskNo;

    /** 工单 ID */
    private Long workOrderId;

    /** 工单编码快照 */
    private String workOrderCode;

    /** ERP 生产订单号快照 */
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

    /** 状态：PLANNED/DISPATCHED/IN_PROGRESS/PAUSED/COMPLETED/SCRAPPED */
    private String status;

    /** 优先级：NORMAL / URGENT */
    private String priority;

    /** 计划开工时间 */
    private LocalDateTime plannedStart;

    /** 计划完工时间 */
    private LocalDateTime plannedEnd;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
