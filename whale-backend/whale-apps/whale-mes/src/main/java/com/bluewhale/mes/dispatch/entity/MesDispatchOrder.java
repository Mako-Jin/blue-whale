package com.bluewhale.mes.dispatch.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 派工单实体，对应表 {@code mes_dp_dispatch_order}。
 *
 * <p>一次批量派工生成一张派工单，明细记录被派工的任务与数量；派工单仅允许在任务未开工时撤回。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_dp_dispatch_order")
public class MesDispatchOrder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 派工单号（租户内唯一） */
    private String dispatchNo;

    /** 工单 ID */
    private Long workOrderId;

    /** 工单编码快照 */
    private String workOrderCode;

    /** 派工产线 ID */
    private Long lineId;

    /** 派工产线编码 */
    private String lineCode;

    /** 派工班组 ID */
    private Long teamId;

    /** 派工班组编码 */
    private String teamCode;

    /** 任务数 */
    private Integer taskCount;

    /** 派工总数量 */
    private BigDecimal dispatchQuantity;

    /** 状态：DISPATCHED/WITHDRAWN */
    private String status;

    /** 派工人 */
    private String dispatchedBy;

    /** 派工时间 */
    private LocalDateTime dispatchedAt;

    /** 撤回时间 */
    private LocalDateTime withdrawnAt;

    /** 备注 */
    private String remark;
}
