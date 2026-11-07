package com.bluewhale.oa.administrative.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用车预约实体，对应表 {@code oa_vehicle_booking}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_vehicle_booking")
public class VehicleBookingEntity extends BaseEntity {

    private String code;

    private String applicant;

    private Long applicantId;

    private Long deptId;

    private Long vehicleId;

    private Long driverId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String origin;

    private String destination;

    private String reason;

    private String status;

    private Long approvalInstanceId;

    private String remark;
}
