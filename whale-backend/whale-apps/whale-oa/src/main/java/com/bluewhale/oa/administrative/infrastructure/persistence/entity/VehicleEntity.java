package com.bluewhale.oa.administrative.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 车辆实体，对应表 {@code oa_vehicle}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_vehicle")
public class VehicleEntity extends BaseEntity {

    /** 车牌号 */
    private String plateNo;

    /** 车型 */
    private String vehicleType;

    /** 座位数 */
    private Integer seats;

    /** 状态 */
    private String status;

    /** 当前里程 */
    private BigDecimal currentMileage;

    /** 购置日期 */
    private LocalDate purchaseDate;

    /** 备注 */
    private String remark;
}
