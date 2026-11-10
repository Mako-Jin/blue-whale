package com.bluewhale.oa.hr.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 假期额度流水实体，对应表 {@code oa_leave_balance_log}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_leave_balance_log")
public class LeaveBalanceLogEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 额度ID */
    private Long balanceId;

    /** 用户ID */
    private Long userId;

    /** 假期类型ID */
    private Long leaveTypeId;

    /** 额度年份 */
    private Integer balanceYear;

    /** 动作 */
    private String action;

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
}
