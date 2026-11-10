package com.bluewhale.mes.equipment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 维修工单实体，对应表 {@code mes_eq_repair_order}。
 *
 * <p>状态机 {@code REPORTED → ASSIGNED → REPAIRING → VERIFIED → CLOSED}；
 * 维修完成必须验证后才能关闭，停机时长可由状态事件汇总回填。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_eq_repair_order")
public class MesRepairOrder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 维修工单号（租户内唯一） */
    private String repairNo;

    /** 设备 ID */
    private Long equipmentId;

    /** 设备编码快照 */
    private String equipmentCode;

    /** 故障描述 */
    private String faultDesc;

    /** 严重度：MINOR / MAJOR / CRITICAL */
    private String severity;

    /** 状态：REPORTED / ASSIGNED / REPAIRING / VERIFIED / CLOSED */
    private String status;

    /** 报修人 */
    private String reportedBy;

    /** 报修时间 */
    private LocalDateTime reportedAt;

    /** 维修人 */
    private String assignee;

    /** 派工时间 */
    private LocalDateTime assignedAt;

    /** 开始维修时间 */
    private LocalDateTime startedAt;

    /** 维修说明 */
    private String repairDesc;

    /** 验证人 */
    private String verifiedBy;

    /** 验证时间 */
    private LocalDateTime verifiedAt;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    /** 验证结论 */
    private String verifyConclusion;

    /** 停机时长（分钟） */
    private BigDecimal downtimeMinutes;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
