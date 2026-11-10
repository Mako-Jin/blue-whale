package com.bluewhale.oa.administrative.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 出车登记实体，对应表 {@code oa_vehicle_usage}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_vehicle_usage")
public class VehicleUsageEntity extends BaseEntity {

    private Long bookingId;

    private Long vehicleId;

    private BigDecimal startMileage;

    private BigDecimal endMileage;

    private BigDecimal fuelCost;

    private String remark;
}
