package com.bluewhale.mes.execution.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产报工实体，对应表 {@code mes_ex_production_report}。
 *
 * <p>报工为追加式事实：提交后不可修改，修正通过冲销/补录实现；合格数 + 不良数 = 报工数。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_ex_production_report")
public class MesProductionReport extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 报工单号（租户内唯一） */
    private String reportNo;

    /** 任务 ID */
    private Long taskId;

    /** 任务编号快照 */
    private String taskNo;

    /** 工单 ID */
    private Long workOrderId;

    /** 工单编码快照 */
    private String workOrderCode;

    /** 工序编码快照 */
    private String operationCode;

    /** 工位 ID */
    private Long stationId;

    /** 工位编码快照 */
    private String stationCode;

    /** 报工数量 */
    private BigDecimal reportQuantity;

    /** 合格数量 */
    private BigDecimal qualifiedQuantity;

    /** 不良数量 */
    private BigDecimal defectQuantity;

    /** 状态：SUBMITTED */
    private String status;

    /** 报工人 */
    private String reporter;

    /** 报工时间 */
    private LocalDateTime reportedAt;

    /** 备注 */
    private String remark;
}
