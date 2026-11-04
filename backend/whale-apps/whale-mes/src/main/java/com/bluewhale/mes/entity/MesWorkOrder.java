package com.bluewhale.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产工单实体，对应表 {@code mes_work_order}。
 *
 * <p>公共字段（id / create_time / update_time / deleted）继承自 {@link BaseEntity}，
 * 其余字段与 {@code db/migration/V2__mes_foundation.sql} 中的 {@code mes_work_order} 列逐字段对齐。</p>
 *
 * <p>工单生命周期：{@code RECEIVED → DISPATCHED → IN_PROGRESS ⇄ PAUSED → COMPLETED → CLOSED}，
 * 任意可执行态可转 {@code CANCELLED}；{@code CLOSED / CANCELLED} 为终态。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_work_order")
public class MesWorkOrder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 工单编码（租户内唯一） */
    private String code;

    /** 工单名称 */
    private String name;

    /** 状态：RECEIVED/DISPATCHED/IN_PROGRESS/PAUSED/COMPLETED/CLOSED/CANCELLED */
    private String status;

    /** ERP 生产订单号（租户内唯一，MES 侧幂等键） */
    private String erpOrderNo;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 计划数量 */
    private BigDecimal plannedQuantity;

    /** 计量单位 */
    private String uom;

    /** 工艺路线版本快照 */
    private String routingVersion;

    /** 优先级：NORMAL / URGENT */
    private String priority;

    /** 计划开工时间 */
    private LocalDateTime plannedStart;

    /** 计划完工时间 */
    private LocalDateTime plannedEnd;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    /** 备注 */
    private String remark;
}
