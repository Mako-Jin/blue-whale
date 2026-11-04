package com.bluewhale.mes.quality.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 返工单实体，对应表 {@code mes_qc_rework_order}。
 *
 * <p>由处置类型 {@code REWORK} 执行时生成，回链执行任务与批次；返工未完成时 NCR 不可关闭。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_qc_rework_order")
public class MesReworkOrder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 返工单号（租户内唯一） */
    private String reworkNo;

    /** NCR ID */
    private Long ncrId;

    /** NCR 编号快照 */
    private String ncrNo;

    /** 处置 ID */
    private Long dispositionId;

    /** 工单 ID */
    private Long workOrderId;

    /** 工单编码快照 */
    private String workOrderCode;

    /** 工序任务 ID */
    private Long execTaskId;

    /** 工序任务号快照 */
    private String execTaskNo;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称快照 */
    private String itemName;

    /** 批次号 */
    private String batchNo;

    /** 返工数量 */
    private BigDecimal quantity;

    /** 状态：CREATED / IN_PROGRESS / COMPLETED / CANCELLED */
    private String status;

    /** 返工原因 */
    private String reason;

    /** 返工人 */
    private String operator;

    /** 开工时间 */
    private LocalDateTime startedAt;

    /** 完工时间 */
    private LocalDateTime completedAt;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
