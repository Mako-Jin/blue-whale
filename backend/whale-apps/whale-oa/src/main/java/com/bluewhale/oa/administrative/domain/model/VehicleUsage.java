package com.bluewhale.oa.administrative.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 出车登记（回车时写入里程与油费）。
 *
 * @author BlueWhale
 */
@Data
public class VehicleUsage {

    /** 主键ID */
    private Long id;

    /** 用车预约ID */
    private Long bookingId;

    /** 车辆ID */
    private Long vehicleId;

    /** 出车里程 */
    private BigDecimal startMileage;

    /** 回车里程 */
    private BigDecimal endMileage;

    /** 油费 */
    private BigDecimal fuelCost;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
