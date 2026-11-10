package com.bluewhale.mes.dispatch.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 派工明细实体，对应表 {@code mes_dp_dispatch_line}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_dp_dispatch_line")
public class MesDispatchLine extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 派工单 ID */
    private Long dispatchOrderId;

    /** 任务 ID */
    private Long taskId;

    /** 任务编号快照 */
    private String taskNo;

    /** 工序编码快照 */
    private String operationCode;

    /** 派工数量 */
    private BigDecimal dispatchQuantity;

    /** 状态：DISPATCHED/WITHDRAWN */
    private String status;

    /** 备注 */
    private String remark;
}
