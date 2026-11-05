package com.bluewhale.oa.hr.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 年度假期额度实体，对应表 {@code oa_leave_balance}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_leave_balance")
public class LeaveBalanceEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 假期类型ID */
    private Long leaveTypeId;

    /** 额度年份 */
    private Integer balanceYear;

    /** 总额度 */
    private BigDecimal totalDays;

    /** 已使用 */
    private BigDecimal usedDays;

    /** 冻结中 */
    private BigDecimal frozenDays;

    /** 备注 */
    private String remark;
}
