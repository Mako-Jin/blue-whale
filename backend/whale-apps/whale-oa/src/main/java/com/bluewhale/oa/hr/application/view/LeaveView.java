package com.bluewhale.oa.hr.application.view;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 请假申请视图。
 *
 * @author BlueWhale
 */
@Data
public class LeaveView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 申请单号 */
    private String code;

    /** 申请人账号 */
    private String applicant;

    /** 请假类型 */
    private String leaveType;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 请假天数 */
    private BigDecimal days;

    /** 请假事由 */
    private String reason;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
