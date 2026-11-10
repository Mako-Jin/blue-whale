package com.bluewhale.oa.hr.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 出差行程实体，对应表 {@code oa_trip_itinerary}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_trip_itinerary")
public class TripItineraryEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 出差申请ID */
    private Long tripId;

    /** 行程序号 */
    private Integer dayNo;

    /** 行程日期 */
    private LocalDate tripDate;

    /** 出发地 */
    private String fromPlace;

    /** 到达地 */
    private String toPlace;

    /** 交通方式 */
    private String transport;

    /** 备注 */
    private String remark;
}
