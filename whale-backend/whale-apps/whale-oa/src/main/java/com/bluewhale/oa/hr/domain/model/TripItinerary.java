package com.bluewhale.oa.hr.domain.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 出差行程。
 *
 * <p>行程随出差单一起保存，序号在单内唯一且日期须落在出差起止日期内。</p>
 *
 * @author BlueWhale
 */
@Data
public class TripItinerary {

    /** 主键 */
    private Long id;

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
    private TripTransportType transport;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
