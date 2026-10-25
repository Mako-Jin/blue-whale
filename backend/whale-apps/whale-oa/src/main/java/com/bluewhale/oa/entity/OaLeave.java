package com.bluewhale.oa.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 请假申请实体，对应表 {@code oa_leave}。
 *
 * <p>公共字段（id / create_time / update_time / deleted）继承自 {@link BaseEntity}，
 * 其余字段与 {@code db/init.sql} 中的 {@code oa_leave} 列逐字段对齐。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_leave")
public class OaLeave extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 申请单号（租户内唯一） */
    private String code;

    /** 申请人账号 */
    private String applicant;

    /** 请假类型：ANNUAL / SICK / PERSONAL */
    private String leaveType;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 请假天数（0.5 步进） */
    private BigDecimal days;

    /** 请假事由 */
    private String reason;

    /** 状态：PENDING / APPROVED / REJECTED / CANCELLED */
    private String status;

    /** 备注 */
    private String remark;
}
