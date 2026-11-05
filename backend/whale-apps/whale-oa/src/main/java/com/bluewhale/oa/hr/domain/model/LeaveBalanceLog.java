package com.bluewhale.oa.hr.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 假期额度流水（只追加，不可修改）。
 *
 * <p>{@code days} 恒为正数，方向由 {@link LeaveBalanceAction} 决定；
 * 变更前后快照用于审计与对账。</p>
 *
 * @author BlueWhale
 */
@Data
public class LeaveBalanceLog {

    /** 主键 */
    private Long id;

    /** 额度ID */
    private Long balanceId;

    /** 用户ID */
    private Long userId;

    /** 假期类型ID */
    private Long leaveTypeId;

    /** 额度年份 */
    private Integer balanceYear;

    /** 动作 */
    private LeaveBalanceAction action;

    /** 变动天数（正数） */
    private BigDecimal days;

    /** 变更前总额度 */
    private BigDecimal beforeTotal;

    /** 变更后总额度 */
    private BigDecimal afterTotal;

    /** 变更前已使用 */
    private BigDecimal beforeUsed;

    /** 变更后已使用 */
    private BigDecimal afterUsed;

    /** 变更前冻结 */
    private BigDecimal beforeFrozen;

    /** 变更后冻结 */
    private BigDecimal afterFrozen;

    /** 业务类型 */
    private String bizType;

    /** 业务单据ID */
    private Long bizId;

    /** 操作人用户ID */
    private Long operatorId;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
