package com.bluewhale.oa.hr.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 请假申请实体，对应表 {@code oa_leave}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_leave")
public class LeaveEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

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
}
