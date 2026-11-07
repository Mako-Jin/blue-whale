package com.bluewhale.oa.administrative.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 车辆主数据。
 *
 * @author BlueWhale
 */
@Data
public class Vehicle {

    /** 主键ID */
    private Long id;

    /** 车牌号 */
    private String plateNo;

    /** 车型 */
    private String vehicleType;

    /** 核定座位数 */
    private Integer seats;

    /** 状态 ENABLED/DISABLED */
    private String status;

    /** 当前里程（公里） */
    private BigDecimal currentMileage;

    /** 购置日期 */
    private LocalDate purchaseDate;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
