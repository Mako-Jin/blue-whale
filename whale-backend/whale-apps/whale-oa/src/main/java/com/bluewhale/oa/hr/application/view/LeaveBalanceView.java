package com.bluewhale.oa.hr.application.view;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 年度假期额度视图。
 *
 * @author BlueWhale
 */
@Data
public class LeaveBalanceView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

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

    /** 可用额度 */
    private BigDecimal availableDays;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
