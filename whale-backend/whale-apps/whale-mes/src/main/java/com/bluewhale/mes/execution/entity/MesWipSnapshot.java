package com.bluewhale.mes.execution.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 在制品快照实体，对应表 {@code mes_ex_wip_snapshot}。
 *
 * <p>每个任务一行，报工/完工/报废时重算：在制品 = 已派工 − 已完工 − 已报废，且不得为负。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_ex_wip_snapshot")
public class MesWipSnapshot extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 工单 ID */
    private Long workOrderId;

    /** 任务 ID */
    private Long taskId;

    /** 任务编号快照 */
    private String taskNo;

    /** 工序编码快照 */
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
