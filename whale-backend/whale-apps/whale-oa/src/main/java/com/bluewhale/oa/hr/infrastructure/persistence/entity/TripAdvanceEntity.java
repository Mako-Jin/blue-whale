package com.bluewhale.oa.hr.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 出差预支实体，对应表 {@code oa_trip_advance}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_trip_advance")
public class TripAdvanceEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 出差申请ID */
    private Long tripId;

    /** 申请人用户ID */
    private Long applicantId;

    /** 预支金额 */
    private BigDecimal amount;

    /** 预支状态 */
    private String status;
}
