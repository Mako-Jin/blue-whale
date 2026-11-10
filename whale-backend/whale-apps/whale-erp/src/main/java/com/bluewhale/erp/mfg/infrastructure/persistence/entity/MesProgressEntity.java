package com.bluewhale.erp.mfg.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * MES 进度快照，对应表 {@code erp_mfg_mes_progress}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_mfg_mes_progress")
public class MesProgressEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 生产订单 ID */
    private Long orderId;

    /** ERP 生产订单号 */
    private String erpOrderNo;

    /** MES 工单状态 */
    private String mesStatus;

    /** MES 计划数量 */
    private BigDecimal plannedQuantity;

    /** MES 累计报工数量 */
    private BigDecimal reportedQuantity;

    /** MES 累计合格数量 */
    private BigDecimal qualifiedQuantity;

    /** MES 累计完工数量 */
    private BigDecimal completedQuantity;

    /** MES 任务明细 JSON */
    private String taskJson;

    /** 最近事件幂等键 */
    private String eventId;

    /** 接收时间 */
    private LocalDateTime receivedAt;
}
